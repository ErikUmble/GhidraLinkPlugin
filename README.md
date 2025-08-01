# GhidraLinkPlugin

## About
To document findings from reverse engineering, it can be helpful to reference sections of a binary analyzed in Ghidra. This Ghidra plugin adds support for links that cause a listing view navigation when clicked.



https://github.com/user-attachments/assets/ccfe7a5a-f0c0-42c5-bf6d-db93994eac1f



The GhidraLinkPlugin listens for urls of the form `ghidra://filename#address` sent to the socket on port 24437. 
If `filename` is the name of a program file in the open Ghidra project, this file is opened in the current tool and `address` is navigated to. The file may be nested inside folders in the Ghidra project, so long as its name is distinct.

To create a link, right click the instruction in the listing view and choose `Copy Ghidra Link`. Or simply press CTR + SHIFT + C while your cursor is on the instruction you wish to link to.

## Install Ghidra Plugin
1. Download the latest release [here](https://github.com/ErikUmble/GhidraLinkPlugin/releases)
1. Copy `ghidra_11.3.2_PUBLIC_20250729_GhidraLinkPlugin.zip` to `<ghidra installation directory>/Extensions/Ghidra/`
1. In Ghidra project manager go to `File > Install Extension`
1. Check the `GhidraLinkPlugin` and click OK
1. Restart Ghidra
1. Open the Code Browser with any of the project programs
1. Use `File > Configure > Miscillaneous` and check the `GhidraLinkPlugin` to activate
1. Test the installation by running  
```bash
echo "ghidra://filename#address" | nc localhost 24437
```
in a terminal (replacing filename and address based on your project files) and verifying that the listing view navigated to that offset address.

## Configure Link Handler
When a `ghidra://` link is clicked, we want
the link to be sent to that socket. Use any of the following configurations, depending on your system and requirements.

### xdg-open (Linux)
1. Copy `ghidra_open_link.sh` or `ghidra_open_link.pyw` to `/usr/bin/` (or your preferred location)
1. Use `chmod +x path/to/ghidra_open_link.*` to make it executable
1. Copy `ghidra.desktop` to `~/.local/share/applications/`
1. Modify the `Exec` line of `ghidra.desktop` to match your path for the `ghidra_open_link.*` executable
1. Run `xdg-mime default ghidra.desktop x-scheme-handler/ghidra`
1. Run `xdg-settings set default-url-scheme-handler ghidra ghidra.desktop`

### Windows
1. Copy `ghidra_open_link.pyw` to a permanent location, such as `C:\Scripts\`
1. Modify the command in `ghidra.reg` to match the paths of your Python installation and the location of `ghidra_open_link.pyw`
1. Run `ghidra.reg` by double clicking and confirming to add to registry

### VS Code
Unfortunately, VS code does not yet support custom url protocols yet. A longstanding [issue](https://github.com/microsoft/vscode/issues/133278) for this exists, so it may work natively (using xdg-open, start, etc. based on the OS) in the future. I have tried using a markdown plugin to render ghidra links as VS commands, but this does not work in jupyter notebook cells. I've also tried creating a Code Lens popup on ghidra links, which works well for markdown and jupyer cells in edit mode, but not in preview mode. I also tried making a custom document link provider, but this similarly fails in the rendered markdown. For now, it seems best to use jupyter code cells using `xdg-opn` (or `echo <link> | nc localhost 24437`).
