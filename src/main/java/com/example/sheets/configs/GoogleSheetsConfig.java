package com.example.sheets.configs;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
public class GoogleSheetsConfig {

    private static final String APPLICATION_NAME = "YourAppName";
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static com.google.api.client.http.HttpTransport HTTP_TRANSPORT;

    @Value("${google.service.account.key.path}")
    private String serviceAccountKeyPath;

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Bean
    public Sheets sheetsService() throws IOException {
        InputStream inputStream = new ClassPathResource(serviceAccountKeyPath).getInputStream();
        Credential credential = GoogleCredential.fromStream(inputStream)
                .createScoped(Collections.singletonList(SheetsScopes.SPREADSHEETS));
        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}