package client.i2p;

import net.i2p.I2PException;
import net.i2p.client.*;
import net.i2p.data.Destination;
import net.i2p.data.PrivateKeyFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Main {
    private static class Client {

        private final I2PSession session;
        private final Destination destination;

        public Client(String input, String output) throws IOException, I2PException {
            Properties clientProperties = new Properties();
            clientProperties.setProperty("i2cp.closeIdleTime", "1800000");
            OutputStream outputStream = new FileOutputStream(output);
            InputStream inputStream = new FileInputStream(input);
            I2PClient client = I2PClientFactory.createClient();
            destination = client.createDestination(outputStream);
            session = client.createSession(inputStream, clientProperties);
        }

        public void connect() throws I2PSessionException {
            session.connect();
        }

        public void send(String message) throws I2PSessionException {
            session.sendMessage(destination, message.getBytes());
        }

        public String receive(int messageId) throws I2PSessionException {
            return new String(session.receiveMessage(messageId));
        }
    }

    public static void main(String[] args) throws I2PException, IOException {
        String senderClientPrivateKeyFileName = "sender_client.dat";
        String receiverClientPrivateKeyFileName = "receiver_client.dat";

        // Clients private keys generation if not exist
        if (!Files.exists(Path.of(senderClientPrivateKeyFileName)))
            PrivateKeyFile.main(new String[] { senderClientPrivateKeyFileName });
        if (!Files.exists(Path.of(receiverClientPrivateKeyFileName)))
            PrivateKeyFile.main(new String[] { receiverClientPrivateKeyFileName });

        Client client1 = new Client(receiverClientPrivateKeyFileName, senderClientPrivateKeyFileName);
        Client client2 = new Client(senderClientPrivateKeyFileName, receiverClientPrivateKeyFileName);

        ScheduledThreadPoolExecutor service = new ScheduledThreadPoolExecutor(2);

        service.scheduleAtFixedRate(() -> {
            try {
                System.out.println("CLIENT_1: sending message \"MESSAGE\"");
                client1.connect();
                client1.send("MESSAGE");
            } catch (I2PSessionException e) {
                throw new RuntimeException(e);
            }
        }, 1, 5, TimeUnit.SECONDS);

        service.schedule(() -> {
            try {
                client2.connect();
                client2.session.setSessionListener(new I2PSessionListener() {
                    @Override
                    public void messageAvailable(I2PSession session, int msgId, long size) {
                        try {
                            System.out.println("CLIENT_2: received message " + "\"" + client2.receive(msgId) + "\"");
                        } catch (I2PSessionException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void reportAbuse(I2PSession session, int severity) {

                    }

                    @Override
                    public void disconnected(I2PSession session) {
                        System.out.println("CLIENT2: session disconnected");
                    }

                    @Override
                    public void errorOccurred(I2PSession session, String message, Throwable error) {
                        System.out.println("CLIENT2: an error has occurred. Message: " + message);
                        error.printStackTrace();
                    }
                });
            } catch (I2PSessionException e) {
                throw new RuntimeException(e);
            }
        }, 1, TimeUnit.SECONDS);
    }
}
