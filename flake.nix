{
  description = "Lineage Proxy dev shell";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.jdk21
          pkgs.gradle
          pkgs.gnupg
          pkgs.pinentry-curses
        ];
        JAVA_HOME = pkgs.jdk21;
        shellHook = ''
          export GPG_TTY="$(tty)"
          export PINENTRY_PROGRAM="${pkgs.pinentry-curses}/bin/pinentry-curses"
        '';
      };
    };
}
