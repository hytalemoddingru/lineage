# Permissions

Use `PermissionChecker` to evaluate permission strings against a subject.

```kotlin
if (context.permissionChecker.hasPermission(sender, "lineage.example.use")) {
    sender.sendMessage("Allowed")
}
```

`CommandSender` implements `PermissionSubject`, so it can be checked directly.
