package rem.chadtdroid.server;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class GDriveClientTest {

    @Test
    void testListFiles() throws IOException, GeneralSecurityException {
        Drive mockDriveService = mock(Drive.class);
        Drive.Files mockFiles = mock(Drive.Files.class);
        Drive.Files.List mockFilesList = mock(Drive.Files.List.class);
        
        Map<String, String> params = new HashMap<>();
        params.put(GDriveClient.SERVICE_ACCOUNT_KEY, createMockServiceAccountKey());
        params.put(GDriveClient.TARGET_FOLDER_ID, "test-folder-id");

        File file1 = new File();
        file1.setId("file1");
        file1.setName("test-file-1.txt");
        file1.setMimeType("text/plain");

        File file2 = new File();
        file2.setId("file2");
        file2.setName("test-file-2.pdf");
        file2.setMimeType("application/pdf");

        FileList fileList = new FileList();
        fileList.setFiles(Arrays.asList(file1, file2));

        when(mockDriveService.files()).thenReturn(mockFiles);
        when(mockFiles.list()).thenReturn(mockFilesList);
        when(mockFilesList.setQ(anyString())).thenReturn(mockFilesList);
        when(mockFilesList.setFields(anyString())).thenReturn(mockFilesList);
        when(mockFilesList.execute()).thenReturn(fileList);

        GDriveClient client = spy(new GDriveClient(params));
        doReturn(mockDriveService).when(client).getDriveService();

        List<File> result = client.listFiles();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("test-file-1.txt", result.get(0).getName());
        assertEquals("test-file-2.pdf", result.get(1).getName());
        
        verify(mockFilesList).setQ("'test-folder-id' in parents and trashed=false");
        verify(mockFilesList).setFields("nextPageToken, files(id, name, mimeType, size, modifiedTime, createdTime)");
    }

    private String createMockServiceAccountKey() {
        return "{\n" +
               "  \"type\": \"service_account\",\n" +
               "  \"project_id\": \"test-project\",\n" +
               "  \"private_key_id\": \"test-key-id\",\n" +
               "  \"private_key\": \"-----BEGIN PRIVATE KEY-----\\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7VJTUt9Us8cKB\\n-----END PRIVATE KEY-----\\n\",\n" +
               "  \"client_email\": \"test@test-project.iam.gserviceaccount.com\",\n" +
               "  \"client_id\": \"123456789\",\n" +
               "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n" +
               "  \"token_uri\": \"https://oauth2.googleapis.com/token\"\n" +
               "}";
    }
}