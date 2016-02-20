package logger;

import common.network.Json;
import common.network.data.Message;
import common.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Main controller. Controls execution of the program, e.g. checks time limit of
 * the client, handles incoming messages, controls network operations, etc.
 * This is an internal implementation and you do not need to know anything about
 * this class.
 * Please do not change this class.
 */
public class Controller {

    // Logging tag
    private static final String TAG = "Controller";

    // Connection details
    private int port;
    private String host;
    private String token;
    private long retryDelay;
    private String logPath, resultPath;
    private FileOutputStream logOut, resultOut;
    private double score0, score1;

    private Network network;

    // Terminator. Controller waits for this object to be notified. Then it will be terminated.
    private final Object terminator;

    /**
     * Constructor
     *
     * @param hostIP     host address
     * @param hostPort   host port
     * @param token      client token
     * @param retryDelay connection retry delay
     */
    public Controller(String hostIP, int hostPort, String token, long retryDelay, String logPath, String resultPath) {
        this.terminator = new Object();
        this.host = hostIP;
        this.port = hostPort;
        this.token = token;
        this.retryDelay = retryDelay;
        this.logPath = logPath;
        this.resultPath = resultPath;
    }

    /**
     * Starts a client by connecting to the server and sending a token.
     */
    public void start() {
        try {
            logOut = new FileOutputStream(logPath);
            network = new Network(this::handleMessage);
            network.setConnectionData(host, port, token);
            while (!network.isConnected()) {
                Thread.sleep(retryDelay);
                network.connect();
            }
            synchronized (terminator) {
                terminator.wait();
            }
            logOut.close();
            resultOut = new FileOutputStream(resultPath);
            resultOut.write(String.format("[%f, %f]\n", score0, score1).getBytes(Charset.forName("UTF-8")));
            resultOut.close();
            network.terminate();
        } catch (Exception e) {
            Log.e(TAG, "Can not start the logger.", e);
        }
    }

    /**
     * Handles incoming message. This method will be called from
     * {@link Network} when a new message is received.
     *
     * @param msg incoming message
     */
    private void handleMessage(Message msg) {
        System.err.println("message " + msg.name + " received");
        switch (msg.name) {
            case "status":
                score0 = msg.args.get(1).getAsDouble();
                score1 = msg.args.get(2).getAsDouble();
                break;
            case "shutdown":
                shutdown();
                return;
        }
        try {
            logOut.write(Json.GSON.toJson(msg).getBytes());
            logOut.write(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void shutdown() {
        synchronized (terminator) {
            terminator.notifyAll();
        }
    }
}
