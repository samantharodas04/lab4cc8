import java.io.*;
import java.net.*;

public class SmtpClient {
    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 25)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            System.out.println(reader.readLine()); // Read server's greeting message

            // Send EHLO/HELO
            writer.write("EHLO localhost\r\n");
            writer.flush();
            System.out.println(reader.readLine()); // Read server's response

            // Send MAIL FROM
            writer.write("MAIL FROM: <sender@example.com>\r\n");
            writer.flush();
            System.out.println(reader.readLine()); // Read server's response

            // Send RCPT TO (Recipient 1)
            writer.write("RCPT TO: <recipient1@example.com>\r\n");
            writer.flush();
            System.out.println(reader.readLine()); // Read server's response

            // Send RCPT TO (Recipient 2)
            writer.write("RCPT TO: <recipient2@example.com>\r\n");
            writer.flush();
            System.out.println(reader.readLine()); // Read server's response

            // Send DATA
            writer.write("DATA\r\n");
            writer.flush();
            System.out.println(reader.readLine()); // Read server's response

            // Send email content
            writer.write("Subject: Test Email\r\n");
            writer.write("From: sender@example.com\r\n");
            writer.write("To: recipient1@example.com, recipient2@example.com\r\n");
            writer.write("\r\n");
            writer.write("This is a test email sent via SMTP.\r\n");
            writer.write(".\r\n"); // Terminate the email content
            writer.flush();

            System.out.println(reader.readLine()); // Read server's response

            // Send QUIT
            writer.write("QUIT\r\n");
            writer.flush();
            System.out.println(reader.readLine()); // Read server's response

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
