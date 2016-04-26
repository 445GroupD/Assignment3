package server;

import org.apache.http.HttpException;
import server.Packet.AppPacket;
import server.Packet.LeaderPacket;
import utils.WebService.RestCaller;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.String.format;
import static server.Packet.AppPacket.PacketType.*;

public class MulticastServer
{
    private static DateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private static final int PORT = 4446;
    private static final String GROUP_IP = "239.255.255.255";
    private ServerState serverState = ServerState.FOLLOWER;
    private final MulticastSocket multicastSocket;
    private final InetAddress group;
    private final int serverId;
    private int leaderId;
    private int term;
    private String outgoingData;
    private long groupCount = 5;

    private final Map<Integer, String> fakeDB = new HashMap<Integer, String>();

    private final Map<Integer, LeaderPacket> outgoingLocalStorage = new ConcurrentHashMap<Integer, LeaderPacket>();
    private final LinkedBlockingQueue<String> linkedBlockingClientMessageQueue = new LinkedBlockingQueue<String>();
    private JTextArea userConsole;
    private JScrollPane scrollpane;
    private JTextArea serverConsole;
    private JTextField userMessageInput;
    private JButton userMessageInputButton;
    private JButton serverStatusButton;
    private JButton serverKillButton;


    public MulticastServer(int serverId, int leaderId, CountDownLatch latch) throws IOException
    {
        this.serverId = serverId;
        this.leaderId = leaderId;
        this.term = 0;

        if(serverId == leaderId) serverState = ServerState.LEADER;

        // Create Socket
        multicastSocket = new MulticastSocket(PORT);
        group = InetAddress.getByName(GROUP_IP);
        multicastSocket.joinGroup(group);

        launchGUI(latch);

        startSendingThread();
        startReceivingThread();
    }

    private void launchGUI(CountDownLatch latch) {
        //1. Create the frame.
        String isLeaderDisplay = serverId == leaderId? "*L* " : "";
        JFrame frame = new JFrame(isLeaderDisplay + "Server #" + serverId + " | " + leaderId);
        frame.setSize(1100, 500);

        //2. Optional: What happens when the frame closes?
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //construct the user's UI
        JPanel userConsolePanel = constructUserConsolePanel();
        //construct the server's UI
        JPanel serverConsolePanel = constructServerConsolePanel();

        //creae the scroll panel
        JPanel scrollPanePanel = new JPanel();
        scrollPanePanel.setSize(500, 500);
        scrollPanePanel.setLayout(new BorderLayout());

        //add the user UI to the scroll panel
        scrollPanePanel.add(userConsolePanel, BorderLayout.WEST);
        //add the sever UI to the scroll panel
        scrollPanePanel.add(serverConsolePanel, BorderLayout.EAST);
        //create the scroll panel with the scroll panel
        scrollpane = new JScrollPane(scrollPanePanel);
        //add the scroll pane to the frame's content pane.
        frame.getContentPane().add(scrollpane, BorderLayout.CENTER);

        //show the frame
        frame.setVisible(true);
        //let the other servers start
        latch.countDown();
    }

    private JPanel constructUserConsolePanel() {
        userConsole = new JTextArea();
        userConsole.setSize(500, 500);
        userConsole.setLineWrap(true);
        userConsole.setWrapStyleWord(true);
        userConsole.setEditable(false);

        userMessageInput = new JTextField(40);
        userMessageInput.setSize(400, 100);

        userMessageInputButton = new JButton("Send");
        userMessageInputButton.setSize(50, 100);

        userMessageInput.addKeyListener(new KeyAdapter() {
            public boolean waitingForClientMessageToSend;

            @Override
            public void keyTyped(KeyEvent keyEvent) {
                super.keyTyped(keyEvent);
                //the enter key returns a '\n' new line char
                if (keyEvent.getKeyChar() == '\n') {
                    userMessageInputButton.doClick();
                }
            }
        });

        userMessageInputButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                userMessageInput.setEditable(false);
                String textFromGUI = userMessageInput.getText();
                addClientMessage(textFromGUI);
                consoleMessage(textFromGUI, 1);
                userMessageInput.setText("");
                userMessageInput.setEditable(true);
            }
        });

        userMessageInputButton.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                userMessageInputButton.doClick();
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseExited(MouseEvent mouseEvent) {

            }
        });

        //add a listener to the userConsole's document to know when text has been added to it
        userConsole.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void removeUpdate(DocumentEvent e) {
                //System.out.println("REMOVE UPDATE");
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                //System.out.println("INSERT UPDATE " + e);
               /* if(e.toString().contains("javax.swing.text.AbstractDocument")) {
                    consolePrompt("What do you want to do: ");
                }*/
                userConsole.setCaretPosition(userConsole.getDocument().getLength());
                scrollToBottom(scrollpane);
            }

            @Override
            public void changedUpdate(DocumentEvent arg0) {
                //System.out.println("CHANGE UPDATE");
            }
        });

        JPanel userConsolePanel = new JPanel();
        userConsolePanel.setSize(500, 500);
        userConsolePanel.setLayout(new BorderLayout());
        userConsolePanel.add(new JLabel("User Console"), BorderLayout.NORTH);

        JPanel userControlsPanel = new JPanel();
        userControlsPanel.setSize(500, 500);
        userControlsPanel.setLayout(new BorderLayout());
        userControlsPanel.add(userMessageInput, BorderLayout.WEST);
        userControlsPanel.add(userMessageInputButton, BorderLayout.EAST);

        userConsolePanel.add(userConsole, BorderLayout.CENTER);
        userConsolePanel.add(userControlsPanel, BorderLayout.SOUTH);
        return userConsolePanel;
    }

    private JPanel constructServerConsolePanel() {
        serverConsole = new JTextArea();
        serverConsole.setSize(400, 500);
        serverConsole.setLineWrap(true);
        serverConsole.setWrapStyleWord(true);
        serverConsole.setEditable(false);

        serverStatusButton = new JButton("Status");
        serverStatusButton.setSize(50, 100);

        serverStatusButton.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                consoleMessage("Show Status", 2);
                debugStatus();
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent) {

            }

            @Override
            public void mouseExited(MouseEvent mouseEvent) {

            }
        });

        //add a listener to the userConsole's document to know when text has been added to it
        serverConsole.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void removeUpdate(DocumentEvent e) {
                //System.out.println("REMOVE UPDATE");
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                //System.out.println("INSERT UPDATE " + e);
               /* if(e.toString().contains("javax.swing.text.AbstractDocument")) {
                    consolePrompt("What do you want to do: ");
                }*/
                userConsole.setCaretPosition(userConsole.getDocument().getLength());
                scrollToBottom(scrollpane);
            }

            @Override
            public void changedUpdate(DocumentEvent arg0) {
                //System.out.println("CHANGE UPDATE");
            }
        });

        serverKillButton = new JButton("Kill");
        serverKillButton.setSize(50, 100);

        JPanel serverControlsPanel = new JPanel();
        serverControlsPanel.setSize(500, 500);
        serverControlsPanel.setLayout(new BorderLayout());
        serverControlsPanel.add(serverStatusButton, BorderLayout.WEST);
        serverControlsPanel.add(serverKillButton, BorderLayout.EAST);

        JPanel serverConsolePanel = new JPanel();
        serverConsolePanel.setSize(500, 500);
        serverConsolePanel.setLayout(new BorderLayout());
        serverConsolePanel.add(new JLabel("Server "+ serverId + " Console"), BorderLayout.NORTH);

        serverConsolePanel.add(serverConsole, BorderLayout.CENTER);
        serverConsolePanel.add(serverControlsPanel, BorderLayout.SOUTH);
        return serverConsolePanel;
    }

    private void scrollToBottom(JScrollPane scrollPane) {
        final JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
        AdjustmentListener downScroller = new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                Adjustable adjustable = e.getAdjustable();
                adjustable.setValue(adjustable.getMaximum());
                verticalBar.removeAdjustmentListener(this);
            }
        };
        verticalBar.addAdjustmentListener(downScroller);
    }

    private void addClientMessage(String textFromGUI) {
        //System.out.println("ADDING CM " + textFromGUI);
        try {
            linkedBlockingClientMessageQueue.put(textFromGUI);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //System.out.println("ADDED CM " + textFromGUI);
    }


    private void startSendingThread()
    {

        Thread outgoing = new Thread(new MulticastServerSender(this));
        outgoing.start();
        consoleMessage("started outgoing thread", 2);
    }

    private void startReceivingThread()
    {

        Thread incoming = new Thread(new MulticastServerReceiver(this));
        incoming.start();
        consoleMessage("started incoming thread", 2);
    }

    private void debugStatus()
    {
        consoleMessage("\n----------------------------- START SERVER STATUS ---------------------------\n", 2);
        consoleMessage("\tid = " + serverId, 2);
        consoleMessage("\tleaderId = " + leaderId, 2);
        consoleMessage("\tterm = " + term, 2);
        consoleMessage("\tContents of FakeDB", 2);

        consoleMessage("\n\t------------------- Start Server DB Logs -------------------", 2);
        for (Map.Entry current : fakeDB.entrySet())
        {
            consoleMessage(format("\n\tLog Index: %s | Log: %s", current.getKey(), current.getValue()), 2);
        }
        consoleMessage("\n\t------------------- END Server DB Logs -------------------", 2);
        consoleMessage("\n----------------------------- END SERVER STATUS ---------------------------\n", 2);
    }

    public int getMajority()
    {
        return (int)Math.floor(groupCount / 2);
    }

    public ServerState getServerState() {
        return serverState;
    }

    public boolean isFollower() {
        return serverState.equals(ServerState.FOLLOWER);
    }

    public boolean isLeader() {
        return serverState.equals(ServerState.LEADER);
    }

    public Map<String, AppPacket> getIncomingLocalStorage() {
        return incomingLocalStorage;
    }

    private final Map<String, AppPacket> incomingLocalStorage = new ConcurrentHashMap<String, AppPacket>();

    public Map<Integer, LeaderPacket> getOutgoingLocalStorage() {
        return outgoingLocalStorage;
    }




    /**
     * Used for servers who are receiving packets from the leader.
     * This is a follower of the leader.
     *
     * It receives commit requests and commands from leading servers, in that order,
     * acks to confirm its agreement that the sender is the leader of the group,
     * and if so, it replicates the leader's incoming log to its own db when it receives the commit command from leader.
     */
    public void followerParse(AppPacket receivedPacket)
    {
        try
        {
            // make sure the packet is from the leader
            if (receivedPacket.getServerId() == leaderId)
            {
                switch (receivedPacket.getType())
                {
                    case ACK:
                        consoleError("SHOULDNT SEE THIS", 2);
                        break;
                    case COMMENT:
                        AppPacket ackPacket = new AppPacket(serverId, ACK, leaderId, term,groupCount, receivedPacket.getSequenceNumber(), receivedPacket.getLogIndex(), "");
                        incomingLocalStorage.put(getIncomingStorageKey(receivedPacket), receivedPacket);
                        multicastSocket.send(ackPacket.getDatagram(group, PORT));
                        consoleMessage("Acking commit request confirmation for " + receivedPacket.toString(), 2);
                        break;
                    case COMMIT:
                        AppPacket localPacketFromIncomingStorage = incomingLocalStorage.get(getIncomingStorageKey(receivedPacket));
                        String receivedLogIndex = receivedPacket.getReadableData();
                        String actualDataFromIncomingStorage = localPacketFromIncomingStorage.getReadableData();

                        fakeDB.put(Integer.parseInt(receivedLogIndex), actualDataFromIncomingStorage);
                        RestCaller.postLog(this, receivedLogIndex, actualDataFromIncomingStorage);
                        consoleMessage("Committed Packet: #%s" + localPacketFromIncomingStorage.toString(), 2);
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (HttpException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private String getIncomingStorageKey(AppPacket receivedPacket) {
        return receivedPacket.getLeaderId() + " " + receivedPacket.getSequenceNumber() + " " + receivedPacket.getTerm();
    }


    /**
     *
     * @param receivedPacket
     */
    public void leaderParse(AppPacket receivedPacket)
    {
        try
        {
            switch (receivedPacket.getType())
            {
                case ACK:
                    LeaderPacket ackedLeaderPacket = outgoingLocalStorage.get(receivedPacket.getSequenceNumber());

                    int committedLogIndex = ackedLeaderPacket.confirm(getMajority(), this, fakeDB);
                    //make sure the log index returned from committing is valid
                    if (committedLogIndex > -1)
                    {
                        consoleMessage("\nLeader Committed " + ackedLeaderPacket.toString() + "\n", 2);
                        //all is well. The log was committed to this leader's persistent db at the committedLogIndex.
                        //send the commit command to all followers if necessary.

                        //we send the current term number of the leader because if it doesn't match what the followers have this packet stored as, they should not commit it to their db
                        AppPacket commitPacket = new AppPacket(serverId, COMMIT, leaderId, term, groupCount, ackedLeaderPacket.getSequenceNumber(), committedLogIndex, committedLogIndex + "");
                        if (term == ackedLeaderPacket.getTerm())
                        {
                            //send the commit command to all followers of this leader
                            multicastSocket.send(commitPacket.getDatagram(group, PORT));
                        }
                        else
                        {
                            //this leader is on the wrong term and thus may or may not be the leader anymore
                            consoleError("Leader is in the wrong term. Cant commit", 2);
                        }
                    }
                    break;
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public String getOutgoingData() {
        return outgoingData;
    }

    public int getLeaderId() {
        return leaderId;
    }

    public int getTerm() {
        return term;
    }

    public InetAddress getGroup() {
        return group;
    }

    public int getPort() {
        return PORT;
    }

    public int getId() {
        return serverId;
    }

    public MulticastSocket getMulticastSocket() {
        return multicastSocket;
    }

    public void clearOutgoingData() {
        outgoingData = "";
    }

    public String getClientMessageToSend() {
        try {
            String take = linkedBlockingClientMessageQueue.take();
            //System.out.println("TAKE: " + take);
            return take;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public enum ServerState
    {
        LEADER(),
        CANIDATE(),
        FOLLOWER();
    }

    public void consoleMessage(String s, int which) {
        if(s != null && !s.trim().isEmpty()) {
            //m stands for message
            switch (which) {
                case 1:
                    userConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|m> " + s);
                    break;
                case 2:
                    serverConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|m> " + s);
                    break;
            }
        }
    }

    protected void consoleError(String s, int which) {
        //e stands for error
        if(s != null && !s.trim().isEmpty()) {
            switch (which) {
                case 1:
                    userConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|e> " + s);
                    break;
                case 2:
                    serverConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|e> " + s);
                    break;
            }
        }
    }

    protected void consolePrompt(String s, int which) {
        //p stands for prompt
        if(s != null && !s.trim().isEmpty()) {
            switch (which) {
                case 1:
                    userConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|p> " + s);
                    break;
                case 2:
                    serverConsole.append("\n" + getCurrentDateTime(null) + " #" + serverId + "|p> " + s);
                    break;
            }
        }
    }

    public static String getCurrentDateTime(DateFormat dateFormat) {
        dateFormat = dateFormat != null? dateFormat : DEFAULT_DATE_FORMAT;
        return dateFormat.format(new Date());
    }
}
