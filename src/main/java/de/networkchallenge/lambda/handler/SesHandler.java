package de.networkchallenge.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SesHandler implements RequestStreamHandler {
    private static AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();
    private static final String ENV_MARKERS_FILE          = "MarkersFile";
    private static final String MARKERS_FILE              = System.getenv(ENV_MARKERS_FILE) == null ? "markers.js"
            : System.getenv(ENV_MARKERS_FILE);
    private static final String ENV_SES_BUCKET_NAME          = "SesBucketName";
    private static final String SES_BUCKET_NAME              = System.getenv(ENV_SES_BUCKET_NAME) == null ? "SesBucket"
            : System.getenv(ENV_SES_BUCKET_NAME);
    private static final String ENV_WEB_BUCKET_NAME          = "WebBucketName";
    private static final String WEB_BUCKET_NAME              = System.getenv(ENV_WEB_BUCKET_NAME) == null ? "WebBucket"
            : System.getenv(ENV_WEB_BUCKET_NAME);

    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        ObjectMapper mapper = new ObjectMapper(); // can reuse, share globally
        String jsonString = convert(input, Charset.forName("utf-8"));
        context.getLogger().log(jsonString);
        SESEvent event = mapper.readValue(jsonString, SESEvent.class);
        String result = checkUserBinded(event, context);
        output.write(result.getBytes("utf-8"));
    }
    public String checkUserBinded(SESEvent event, Context c) {
        c.getLogger().log("Inside the function\n");
        if (event == null || event.getRecords() == null) {
            c.getLogger().log("Events is null\n");
            return "STOP_RULE";
        }
        String messageId = event.getRecords().get(0).getSES().getMail().getMessageId();
        c.getLogger().log("Need to load MessageId from S3: " + messageId);
        String message = s3.getObjectAsString(SES_BUCKET_NAME, messageId);
        Matcher matcher = Pattern.compile(".*Lat (.*) Lon (.*)").matcher(message);
        if (matcher.find())
        {
            String lat = matcher.group(1);
            String lng = matcher.group(2);
            c.getLogger().log("found Lat " + lat + " Long "+ lng);
        }
        else c.getLogger().log("nothing found");
        return "CONTINUE";
    }

    public String convert(InputStream inputStream, Charset charset) throws IOException {

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, charset))) {
            return br.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }
}