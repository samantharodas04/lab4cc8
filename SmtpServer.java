import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.UUID;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

public class SmtpServer {
    private static HashMap<String, String> emails = new HashMap<>();
    private static HashMap<String, String> localUsers = new HashMap<>();

    // Tabla de enrutamiento para asociar destinatarios con servidores SMTP externos
    private static HashMap<String, String> externalServers = new HashMap<>();

    static {
        // Agregar usuarios locales al servidor (solo para fines de demostración)
        localUsers.put("user1@example.com", "User 1");
        localUsers.put("user2@example.com", "User 2");

        // Agregar entradas a la tabla de enrutamiento (Para fines de demostración)
        externalServers.put("example.com", "external-smtp-server1.example.com");
        externalServers.put("example.net", "external-smtp-server2.example.net");
        // Agrega más entradas según tus necesidades
    }

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(25)) {
            System.out.println("SMTP Server is running and waiting for connections...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from: " + clientSocket.getInetAddress().getHostAddress());

                // Handle the client connection in a separate thread
                new SmtpServerHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized String storeEmail(String message) {
        String emailId = UUID.randomUUID().toString();
        emails.put(emailId, message);
        return emailId;
    }

    public static synchronized boolean isLocalUser(String recipient) {
        return localUsers.containsKey(recipient);
    }

    public static String getExternalServerAddressForRecipient(String recipient) {
        // Obtener el dominio del destinatario (parte después del símbolo @)
        int atIndex = recipient.indexOf("@");
        if (atIndex == -1) {
            return null; // El destinatario no es válido (no tiene @)
        }

        String domain = recipient.substring(atIndex + 1);

        // Buscar en la tabla de enrutamiento para obtener la dirección del servidor externo
        return externalServers.get(domain);
    }
}

class SmtpServerHandler extends Thread {
    private final Socket clientSocket;
    private final StringBuilder message;
    private String sender;
    private String recipient;

    public SmtpServerHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.message = new StringBuilder();
        this.sender = null;
        this.recipient = null;
    }

    @Override
    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            writer.write("220 localhost SMTP Server ready\r\n");
            writer.flush();

            String line;
            boolean isData = false;

            while ((line = reader.readLine()) != null) {
                System.out.println("Received: " + line);

                if (line.toUpperCase().startsWith("HELO") || line.toUpperCase().startsWith("EHLO")) {
                    writer.write("250 Hello " + clientSocket.getInetAddress().getHostName() + ", pleased to meet you\r\n");
                } else if (line.toUpperCase().startsWith("MAIL FROM:")) {
                    writer.write("250 Sender OK\r\n");
                    sender = line.substring(10).trim();
                } else if (line.toUpperCase().startsWith("RCPT TO:")) {
                    recipient = line.substring(8).trim();
                    if (SmtpServer.isLocalUser(recipient)) {
                        writer.write("250 Recipient OK\r\n");
                    } else {
                        writer.write("250 Recipient not found, but will try to forward the email\r\n");
                    }
                } else if (line.toUpperCase().startsWith("DATA")) {
                    if (recipient != null && sender != null) {
                        writer.write("354 Enter message, ending with \".\" on a line by itself\r\n");
                        writer.flush();
                        isData = true;
                    } else {
                        writer.write("503 Bad sequence of commands (No valid RCPT TO or MAIL FROM)\r\n");
                    }
                } else if (line.equals(".")) {
                    if (isData) {
                        if (SmtpServer.isLocalUser(recipient)) {
                            String emailId = SmtpServer.storeEmail(message.toString());
                            writer.write("250 Message accepted for delivery, assigned ID: " + emailId + "\r\n");
                            message.setLength(0);
                            isData = false;
                        } else {
                            forwardEmailToExternalServer(message.toString(), sender, recipient);
                            writer.write("250 Message forwarded to external server\r\n");
                            message.setLength(0);
                            isData = false;
                        }
                    } else {
                        writer.write("500 Syntax error, command unrecognized\r\n");
                    }
                } else if (line.toUpperCase().startsWith("QUIT")) {
                    writer.write("221 Goodbye\r\n");
                    writer.flush();
                    break;
                } else if (isData) {
                    message.append(line).append("\r\n");
                } else {
                    writer.write("500 Syntax error, command unrecognized\r\n");
                }

                writer.flush();
            }

            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void forwardEmailToExternalServer(String emailContent, String sender, String recipient) {
        // Obtener la dirección del servidor SMTP externo para el destinatario
        String externalServerAddress = SmtpServer.getExternalServerAddressForRecipient(recipient);

        // Conectarse al servidor SMTP externo
        try (Socket socket = new Socket(externalServerAddress, 25)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            // Leer el mensaje de bienvenida del servidor externo
            System.out.println("Connected to external SMTP server");
            System.out.println(reader.readLine());

            // Enviar EHLO/HELO
            writer.write("EHLO localhost\r\n");
            writer.flush();
            System.out.println(reader.readLine());

            // Enviar MAIL FROM
            writer.write("MAIL FROM: <" + sender + ">\r\n");
            writer.flush();
            System.out.println(reader.readLine());

            // Enviar RCPT TO
            writer.write("RCPT TO: <" + recipient + ">\r\n");
            writer.flush();
            System.out.println(reader.readLine());

            // Enviar DATA
            writer.write("DATA\r\n");
            writer.flush();
            System.out.println(reader.readLine());

            // Enviar contenido del correo
            writer.write(emailContent);
            writer.flush();

            // Enviar punto para terminar el correo
            writer.write("\r\n.\r\n");
            writer.flush();
            System.out.println(reader.readLine());

            // Enviar QUIT
            writer.write("QUIT\r\n");
            writer.flush();
            System.out.println(reader.readLine());

            System.out.println("Email forwarded successfully");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
