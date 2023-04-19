package cz.cas.lib.proarc.common.export.bagit;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import cz.cas.lib.proarc.common.config.AppConfiguration;
import cz.cas.lib.proarc.common.export.mets.MetsUtils;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;

public class BagitExport {

    private File exportFolder;
    private File bagitFolder;
    private final AppConfiguration appConfiguration;


    public BagitExport(AppConfiguration appConfiguration, File exportFolder) {
        this.appConfiguration = appConfiguration;
        this.exportFolder = exportFolder;
    }


    public void bagit() throws IOException {
        BagitProcess process = new BagitProcess(appConfiguration.getBagitExportPosProcessor(), exportFolder);
        if (process != null) {
            process.run();

            if (!process.isOk()) {
                throw new IOException("Processing Bagit failed. \n" + process.getFullOutput());
            }
        }
    }

    public void zip() throws IOException {
//        File newFile = new File(exportFolder, exportFolder.getName());
//        newFile.createNewFile();
        File tmpFile = new File(exportFolder.getParentFile(), exportFolder.getName() + "_tmp");
        tmpFile.mkdir();
        exportFolder.renameTo(new File(tmpFile, exportFolder.getName()));
        File file2Zip = new File(tmpFile.getParentFile(), tmpFile.getName().substring(0, tmpFile.getName().length() - 4));
        tmpFile.renameTo(file2Zip);
        File zipFileName = createZipFile();
        try {
            ZipFile zipFile = new ZipFile(zipFileName);
            ZipParameters zipParameters = new ZipParameters();
            zipParameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_STANDARD);
            zipParameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
            zipParameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
            zipParameters.setIncludeRootFolder(true);
            zipParameters.setDefaultFolderPath(file2Zip.getAbsolutePath());
            zipFile.addFiles(listZipFiles(file2Zip), zipParameters);
        } catch (ZipException e) {
            throw new RuntimeException(e);
        }

    }

    private ArrayList listZipFiles(File exportFolder) {
        ArrayList<File> files = new ArrayList<File>();
        ArrayList<File> subfiles = new ArrayList<File>();
        for (File file : exportFolder.listFiles()) {
            if (file.isFile()) {
                files.add(file);
            } else if (file.isDirectory()) {
                subfiles.addAll(listZipFiles(file));
            }
        }
        files.addAll(subfiles);
        return files;
    }

    public void deleteExportFolder() {
        MetsUtils.deleteFolder(exportFolder);
    }

    private File createZipFile() {
        return new File(exportFolder.getAbsolutePath() + ".zip");
    }

    public static File findExportFolder(File userExportFolder, String folderName) {
        if (folderName.startsWith("uuid:")) {
            folderName = folderName.substring(5);
        }
        for (File file : userExportFolder.listFiles()) {
            if (file.isDirectory() && file.exists() && folderName.equals(file.getName())) {
                return file;
            }
        }
        return null;
    }

    public void moveToBagitFolder() throws IOException {
        File parentFile = exportFolder.getParentFile();
        bagitFolder = new File(parentFile, "bagit_" + exportFolder.getName());
        if (bagitFolder.exists()) {
            MetsUtils.deleteFolder(bagitFolder);
        }
        if(!bagitFolder.mkdir()) {
          throw new IOException("Impossible to create folder " + bagitFolder.getName());
        }

        File zipFolder = new File(exportFolder.getAbsolutePath() + ".zip");
        if (!zipFolder.exists()) {
            throw new IOException("Zip file doesn´t exists.");
        } else {
            zipFolder.renameTo(new File(bagitFolder, zipFolder.getName()));
        }
    }

    public void createMd5File() throws IOException, NoSuchAlgorithmException {
        if (!bagitFolder.exists()) {
            throw new IOException("Bagit folder doesn´t exists " + bagitFolder.getName());
        }
        StringBuilder checksumBuilder = new StringBuilder();
        for (File file : bagitFolder.listFiles()) {
//            byte[] bytes = Files.readAllBytes(Paths.get(file.getPath()));
//            byte[] hash = MessageDigest.getInstance("MD5").digest(bytes);
//            String hashValue = DatatypeConverter.printHexBinary(hash);
            ByteSource byteSource = Files.asByteSource(file);
            HashCode hc = byteSource.hash(Hashing.md5());
            checksumBuilder.append("MD5").append(" ").append(hc.toString().toLowerCase());
            }
        File checksumFile = new File(bagitFolder, exportFolder.getName() + ".sums");
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(checksumFile));
            writer.append(checksumBuilder);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    public void prepare() {
        File newName = new File(this.exportFolder.getParentFile(), "archive_" + this.exportFolder.getName());
        this.exportFolder.renameTo(newName);
        this.exportFolder = newName;
    }

    public void moveToSpecifiedDirectories() {
        String bagitExportPath = appConfiguration.getBagitExportPath();
        if (bagitExportPath == null || bagitExportPath.isEmpty()) {
            // nikam se nic nepresouva, zustava v puvodnim adresari
            return;
        } else {
            File bagitExportRoot = new File(bagitExportPath);
            if (!bagitExportRoot.exists()) {
                bagitExportRoot.mkdir();
            }
            for (File bagitFile : bagitFolder.listFiles()) {
                bagitFile.renameTo(new File(bagitExportRoot, bagitFile.getName()));
            }
            MetsUtils.deleteFolder(bagitFolder);
        }
    }
}
