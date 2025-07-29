package ghidralinkplugin;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.*;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import docking.ActionContext;
import docking.action.DockingAction;
import docking.action.KeyBindingData;
import docking.action.MenuData;
import ghidra.app.context.ProgramLocationActionContext;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.app.plugin.ProgramPlugin;
import ghidra.app.services.GoToService;
import ghidra.app.services.ProgramManager;
import ghidra.framework.model.DomainFile;
import ghidra.framework.model.DomainFolder;
import ghidra.framework.model.ProjectData;
import ghidra.framework.plugintool.*;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Program;
import ghidra.program.util.ProgramLocation;

/**
 * Listens for ghidra:// links sent over a socket and provides a context menu
 * action to copy such links to the clipboard.
 */
//@formatter:off
@PluginInfo(
	status = PluginStatus.STABLE,
	packageName = "GhidraLinkPlugin",
	category = PluginCategoryNames.NAVIGATION,
	shortDescription = "Link to Ghidra listing addresses",
	description = "Listens for ghidra:// links sent over a socket and adds a 'Copy Link' action."
)
//@formatter:on
public class GhidraLinkPlugin extends ProgramPlugin {

    private ServerSocket serverSocket;
    private Thread serverThread;
    private static final int PORT = 24437;
    private DockingAction copyLinkAction;

    public GhidraLinkPlugin(PluginTool tool) {
        super(tool, true, true);
    }

    @Override
    protected void init() {
        super.init();
        try {
            serverSocket = new ServerSocket(PORT);
            serverThread = new Thread(this::runServer);
            serverThread.setDaemon(true); // Ensure thread doesn't prevent Ghidra from closing
            serverThread.start();
            System.out.println("GhidraLinkPlugin: Server started on port " + PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        createCopyLinkAction();
    }

    private void runServer() {
        while (serverSocket != null && !serverSocket.isClosed()) {
            try (Socket clientSocket = serverSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

                String urlString = in.readLine();
                if (urlString != null && !urlString.trim().isEmpty()) {
                    System.out.println("GhidraLinkPlugin: Received URL: " + urlString);
                    try {
                        URI uri = new URI(urlString);
                        if (!"ghidra".equalsIgnoreCase(uri.getScheme())) {
                            System.err.println("GhidraLinkPlugin: Invalid URI scheme.");
                            continue;
                        }
                        // The authority part of the URI is the filename
                        String filename = uri.getAuthority();
                        // The fragment part (after #) is the address
                        String address = uri.getFragment();

                        if (filename != null && address != null) {
                            // UI operations must be run on the Event Dispatch Thread (EDT)
                            SwingUtilities.invokeLater(() -> handleNavigation(filename, address));
                        }
                    } catch (URISyntaxException e) {
                        System.err.println("GhidraLinkPlugin: Could not parse received URL: " + urlString);
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                if (serverSocket.isClosed()) {
                    System.out.println("GhidraLinkPlugin: Server socket closed, shutting down.");
                } else {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleNavigation(String filename, String addressStr) {
        ProjectData projectData = tool.getProject().getProjectData();
        DomainFile targetFile = findFile(projectData.getRootFolder(), filename);

        if (targetFile == null) {
            System.err.println("GhidraLinkPlugin: Unable to find file '" + filename + "' in the current project.");
            return;
        }

        ProgramManager pm = tool.getService(ProgramManager.class);
        Program program = pm.openProgram(targetFile);

        GoToService goToService = tool.getService(GoToService.class);
        if (goToService == null) {
            System.err.println("GhidraLinkPlugin: GoToService not available.");
            return;
        }

        Address address = program.getAddressFactory().getAddress(addressStr);
        goToService.goTo(new ProgramLocation(program, address), program);
    }

    private DomainFile findFile(DomainFolder folder, String filename) {
        for (DomainFile file : folder.getFiles()) {
            if (file.getName().equals(filename)) {
                return file;
            }
        }
        for (DomainFolder subFolder : folder.getFolders()) {
            DomainFile found = findFile(subFolder, filename);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
    
    private void createCopyLinkAction() {
        copyLinkAction = new DockingAction("Copy Ghidra Link", getName()) {
            @Override
            public void actionPerformed(ActionContext context) {
            	if (!(context instanceof ProgramLocationActionContext)) return;
            	ProgramLocationActionContext programContext = (ProgramLocationActionContext) context;
                
            	ProgramLocation location = programContext.getLocation();
                Program program = programContext.getProgram();

                if (location == null || program == null) {
                    return;
                }

                String programName = program.getDomainFile().getName();
                String address = location.getAddress().toString();

                // URL Encode the program name to handle spaces and special characters
                String encodedProgramName = "";
                try {
                    encodedProgramName = URLEncoder.encode(programName, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    // This should never happen with UTF-8
                    encodedProgramName = programName;
                }

                String link = String.format("ghidra://%s#%s", encodedProgramName, address);

                // Copy the link to the system clipboard
                StringSelection selection = new StringSelection(link);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selection, selection);
                
                tool.setStatusInfo("Copied to clipboard: " + link);
            }

            @Override
            public boolean isEnabledForContext(ActionContext context) {
            	// Only enable the action if a program is open and the cursor is at a valid location
            	return context instanceof ProgramLocationActionContext;
            }
        };

        copyLinkAction.setPopupMenuData(new MenuData(new String[] { "Copy Ghidra Link" }, "navigation"));
        
        // Assign the keyboard shortcut Ctrl+Shift+C
        copyLinkAction.setKeyBindingData(new KeyBindingData(
            KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));

        tool.addAction(copyLinkAction);
    }

    @Override
    protected void dispose() {
        super.dispose();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (serverThread != null) {
                serverThread.interrupt();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("GhidraLinkPlugin: Disposed.");
    }
}
