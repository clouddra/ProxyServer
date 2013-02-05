
/**
 * @author Chong Yun Long A0072292H
 */
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/**
 *
 * Use telnet to test the echo server: $ javac ThreadedEchoServer.java $ java
 * ThreadedEchoServer $ telnet localhost <port number>
 *
 *
 * Alternatively you may use a browser to test
 */
public class HTTPProxy {

    public static void main(String[] args) {

        HashSet<String> filterList = setFilter();
        try {
            int i = 1;
            ServerSocket s = new ServerSocket(getPort());

            while (true) {
                Socket incoming = s.accept();
                System.out.println("Spawning " + i);
                Runnable r = new ThreadedConnectionHandler(incoming, i, filterList);
                Thread t = new Thread(r);
                t.start();
                i++;
            }
        } catch (IOException e) {
            System.out.println("I/O exception.");
        }
    }

    public static int getPort() {

        int port;
        while (true) {
            String input = JOptionPane.showInputDialog("Input a number ranging from 0 to 65535 (cancel for default port) :");

            // if user click cancel, default port is 49999
            if (input == null) {
                port = 49999;
            } else {
                try {
                    port = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    continue;
                }
                // ensure port in range
                if (port < 0 || port > 65535) {
                    continue;
                }
            }
            break;
        }

        return port;
    }

    public static HashSet<String> setFilter() {

        BufferedReader in;
        String keyword;
        HashSet<String> filterList = new HashSet<String>();
        File fileDir;

        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new java.io.File("."));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setDialogTitle("Select filter file. Press cancel to disable filtering.");

        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            fileDir = chooser.getSelectedFile();

        } else {
            return filterList;   // if cancel, no filter is applied  
        }

        try {   // read from file and to filter list
            in = new BufferedReader(new FileReader(fileDir));
        } catch (FileNotFoundException ex) {
            return filterList;
        }

        try {
            while ((keyword = in.readLine()) != null) {
                filterList.add(keyword);
            }
        } catch (IOException ex) {
            System.out.println("File cannot be successfully read. No filters will be applied");
        }

        return filterList;



    }
}

/**
 * This class handles the client for one server socket connection.
 */
class ThreadedConnectionHandler implements Runnable {

    public static enum Code {

        OK, METHOD_NOT_ALLOWED, NOT_FOUND, NO_RESPONSE, FORBIDDEN
    };

    /**
     * Constructs a handler.
     *
     * @param i the incoming socket
     * @param c the counter for the handlers (used in prompts)
     */
    public ThreadedConnectionHandler(Socket i, int c, HashSet<String> filter) {
        incoming = i;
        counter = c;
        filterList = filter;
    }

    public String extractURL(String msg) {
        String url;
        url = msg.substring(msg.lastIndexOf("GET") + 4);
        url = url.substring(0, url.indexOf("HTTP"));
        return url;
    }

    /**
     * Construct a HTTP request header from a URL
     *
     * @param url
     * @param rHeader request headers of client request
     * @return
     */
    public String constructRequest(URL url, String rHeader) {
        return "GET " + url.getFile() + " HTTP/1.0\r\n" + rHeader + "\r\n";
    }

    /*
     * Filters the URL
     */
    public boolean filterURL(String rline) {
        Iterator<String> it = filterList.iterator();
        while (it.hasNext()) {
            if (rline.indexOf(it.next()) >= 0) {
                return true;
            }
        }
        return false;
    }

    /*
     * Construct a simple html page based on the type of status code
     */
    public String generateErrorPage(Code error) {

        String status = new String();
        switch (error) {
            case METHOD_NOT_ALLOWED:
                break;

            case NOT_FOUND: // no need to error page
                status = "404 Not Found";
                break;

            case NO_RESPONSE:
                status = "444 No Response";
                break;

            case FORBIDDEN:
                status = "403 Forbidden";
                break;

            default:
                return null;
        }

        return "HTTP/1.0 " + status + "\r\n\r\n<!DOCTYPE html><html><body><h1>" + status + "</h1></body></html>";

    }

    public void run() {
        try {
            try {
                Socket s = null;
                int length;
                byte buf[] = new byte[1024];
                InputStreamReader isrServer = new InputStreamReader(incoming.getInputStream());
                DataOutputStream clientWriter = new DataOutputStream(incoming.getOutputStream());

                DataOutputStream serverWriter;
                DataInputStream serverReader;
                BufferedReader requestReader = new BufferedReader(isrServer);
                String requestHeader = "", msgLine, requestLine;
                Code error = Code.OK;
                URL url = null;

                requestLine = requestReader.readLine();

                if (requestLine != null && requestLine.length() > 0) {

                    // check for GET request
                    if (!requestLine.startsWith("GET")) {
                        error = Code.METHOD_NOT_ALLOWED;
                    }

                    // filter URL
                    String link = extractURL(requestLine);
                    if (filterURL(link)) {
                        error = Code.FORBIDDEN;
                    }

                    // check for server reachability
                    try {
                        url = new URL(link);
                        if (url.getPort() == -1) {
                            s = new Socket(InetAddress.getByName(url.getHost()), 80);
                        } else {
                            s = new Socket(InetAddress.getByName(url.getHost()), url.getPort());
                        }
                    } catch (UnknownHostException | MalformedURLException e) {
                        error = Code.NOT_FOUND;

                    } catch (ConnectException e) {  // if wrong port
                        error = Code.NO_RESPONSE;
                    }

                    // start reading the whole request fomr client and data from server
                    if (error == Code.OK) {

                        // read request header
                        while ((msgLine = requestReader.readLine()) != null && !msgLine.equals("")) {
                            requestHeader += msgLine + "\r\n";
                        }

                        // send request to server
                        serverWriter = new DataOutputStream(s.getOutputStream());
                        serverWriter.write(constructRequest(url, requestHeader).getBytes());

                        // read server's response and send back to client
                        serverReader = new DataInputStream(s.getInputStream());
                        while ((length = serverReader.read(buf)) > 0) {
                            clientWriter.write(buf, 0, length);
                        }

                    } else {
                        String errorPage = generateErrorPage(error);
                        if (errorPage != null) {
                            clientWriter.writeBytes(errorPage);
                        }
                    }
                }
            } finally {
                incoming.close();
            }

        } catch (IOException e) {
            System.out.println("I/O exception.");
        }
    }
    private Socket incoming;
    private int counter;
    private HashSet<String> filterList;
}
