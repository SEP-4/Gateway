package main.java.com.sep4.lorawanwebsocket;

import com.google.gson.Gson;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class SocketClient implements WebSocket.Listener
{
    private WebSocket server = null;

    // Send down-link message to device
    // Must be in Json format according to https://github.com/ihavn/IoT_Semester_project/blob/master/LORA_NETWORK_SERVER.md
    public void sendDownLink(String jsonTelegram) {
        server.sendText(jsonTelegram,true);
    }

    // E.g. url: "wss://iotnet.teracom.dk/app?token=??????????????????????????????????????????????="
    // Substitute ????????????????? with the token you have been given
    public SocketClient(String url) {
        HttpClient client = HttpClient.newHttpClient();
        CompletableFuture<WebSocket> ws = client.newWebSocketBuilder()
                .buildAsync(URI.create(url), this);

        server = ws.join();
    }

    //onOpen()
    public void onOpen(WebSocket webSocket) {
        // This WebSocket will invoke onText, onBinary, onPing, onPong or onClose methods on the associated listener (i.e. receive methods) up to n more times
        webSocket.request(1);
        System.out.println("WebSocket Listener has been opened for requests.");
    }

    //onError()
    public void onError(WebSocket webSocket, Throwable error) {
        System.out.println("A " + error.getCause() + " exception was thrown.");
        System.out.println("Message: " + error.getLocalizedMessage());
        webSocket.abort();
    }

    //onClose()
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println("WebSocket closed!");
        System.out.println("Status:" + statusCode + " Reason: " + reason);
        return new CompletableFuture().completedFuture("onClose() completed.").thenAccept(System.out::println);
    }

    //onPing()
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1);
        System.out.println("Ping: Client ---> Server");
        System.out.println(message.asCharBuffer().toString());
        return new CompletableFuture().completedFuture("Ping completed.").thenAccept(System.out::println);
    }

    //onPong()
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1);
        System.out.println("Pong: Client ---> Server");
        System.out.println(message.asCharBuffer().toString());
        return new CompletableFuture().completedFuture("Pong completed.").thenAccept(System.out::println);
    }

    //onText()
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last)
    {
        String indented = null;
        try
        {
            indented = (new JSONObject(data.toString())).toString(4);
        }
        catch (JSONException e)
        {
            e.printStackTrace();
        }
        System.out.println(indented);

        Gson gson = new Gson();
        Message message = gson.fromJson(indented, Message.class); // no idea if this is how we do it

        if(message.cmd.equals("rx")){
            //call a thing on the database
            String humHex = message.data.substring(0,4);
            String tempHex = message.data.substring(4,8);
            String co2Hex = message.data.substring(8,12);
            Timestamp ts = new Timestamp(message.ts);
            Date date = new Date(message.ts);

            Temperature temp = new Temperature(Float.parseFloat(ConvHelper.convertHexToDecimal(tempHex)), date, ts);
            Humidity hum = new Humidity(Float.parseFloat(ConvHelper.convertHexToDecimal(humHex)), date, ts);
            CO2 co2 = new CO2(Float.parseFloat(ConvHelper.convertHexToDecimal(co2Hex)), date, ts);
            System.out.println(temp.toString());
            System.out.println(hum.toString());
            System.out.println(co2.toString());
            try
            {
                MSSQLDatabase.getInstance().insertTemp(temp);
            }
            catch (SQLException throwables)
            {
                throwables.printStackTrace();
            }
        }

        webSocket.request(1);
        return new CompletableFuture().completedFuture("onText() completed.").thenAccept(System.out::println);
    }
}
