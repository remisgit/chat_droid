package rem.chadtdroid.server;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GDriveClient {
    
    private static final String APPLICATION_NAME = "ChatDroid Server";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    
    public static final String SERVICE_ACCOUNT_KEY = "gdrive.service.account.key";
    public static final String TARGET_FOLDER_ID = "gdrive.target.folder.id";
    public static final String TARGET_FOLDER_NAME = "gdrive.target.folder.name";
    public static final String IMPERSONATE_USER = "gdrive.impersonate.user";
    
    private final Drive driveService;
    private final String targetFolderId;
    private final String targetFolderName;
    
    public GDriveClient(Map<String, String> params) throws IOException, GeneralSecurityException {
        this.targetFolderId = params.get(TARGET_FOLDER_ID);
        this.targetFolderName = params.get(TARGET_FOLDER_NAME);
        this.driveService = buildDriveService(params);
    }
    
    private Drive buildDriveService(Map<String, String> params) throws IOException, GeneralSecurityException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = createCredential(params, httpTransport);
        
        return new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
    
    private Credential createCredential(Map<String, String> params, NetHttpTransport httpTransport) throws IOException {
        String serviceAccountKey = params.get(SERVICE_ACCOUNT_KEY);
        if (serviceAccountKey == null || serviceAccountKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Service account key is required: " + SERVICE_ACCOUNT_KEY);
        }
        
        GoogleCredential credential = GoogleCredential
                .fromStream(new ByteArrayInputStream(serviceAccountKey.getBytes()))
                .createScoped(Collections.singleton(DriveScopes.DRIVE_READONLY));
        
        String impersonateUser = params.get(IMPERSONATE_USER);
        if (impersonateUser != null && !impersonateUser.trim().isEmpty()) {
            credential = credential.createDelegated(impersonateUser);
        }
        
        return credential;
    }
    
    public List<File> listFiles() throws IOException {
        String folderId = determineFolderId();
        
        String query = "'" + folderId + "' in parents and trashed=false";
        
        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("nextPageToken, files(id, name, mimeType, size, modifiedTime, createdTime)")
                .execute();
        
        return result.getFiles();
    }
    
    public List<File> listFiles(String mimeType) throws IOException {
        String folderId = determineFolderId();
        
        String query = "'" + folderId + "' in parents and trashed=false and mimeType='" + mimeType + "'";
        
        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("nextPageToken, files(id, name, mimeType, size, modifiedTime, createdTime)")
                .execute();
        
        return result.getFiles();
    }
    
    private String determineFolderId() throws IOException {
        if (targetFolderId != null && !targetFolderId.trim().isEmpty()) {
            return targetFolderId;
        }
        
        if (targetFolderName != null && !targetFolderName.trim().isEmpty()) {
            return findFolderByName(targetFolderName);
        }
        
        return "root";
    }
    
    private String findFolderByName(String folderName) throws IOException {
        String query = "name='" + folderName + "' and mimeType='application/vnd.google-apps.folder' and trashed=false";
        
        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .execute();
        
        List<File> folders = result.getFiles();
        if (folders.isEmpty()) {
            throw new IllegalArgumentException("Folder not found: " + folderName);
        }
        
        if (folders.size() > 1) {
            throw new IllegalArgumentException("Multiple folders found with name: " + folderName);
        }
        
        return folders.get(0).getId();
    }
    
    public File getFileById(String fileId) throws IOException {
        return driveService.files().get(fileId)
                .setFields("id, name, mimeType, size, modifiedTime, createdTime, parents")
                .execute();
    }
    
    public Drive getDriveService() {
        return driveService;
    }
}