/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.net.handler

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.ReferenceCountUtil
import ru.hytalemodding.lineage.proxy.util.Logging

/**
 * Forwards inbound data to a target channel and closes the target on disconnect.
 */
class StreamBridge(private val target: Channel) : ChannelInboundHandlerAdapter() {
    private val logger = Logging.logger(StreamBridge::class.java)

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (target.isActive) {
            target.writeAndFlush(msg).addListener { future ->
                if (!future.isSuccess) {
                    logger.warn("Stream bridge write failed", future.cause())
                    closeBoth(ctx.channel())
                    return@addListener
                }
            }
        } else {
            ReferenceCountUtil.release(msg)
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        target.close()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.warn("Stream bridge exception", cause)
        closeBoth(ctx.channel())
    }

    private fun closeBoth(source: Channel) {
        if (source.isActive) {
            source.close()
        }
        if (target.isActive) {
            target.close()
        }
    }
}
