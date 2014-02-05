package hudson.plugins.s3.callable;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.plugins.s3.Destination;
import hudson.plugins.s3.FingerprintRecord;
import hudson.plugins.s3.MetadataPair;
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.internal.Mimetypes;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;

public class S3UploadCallable extends AbstractS3Callable implements FileCallable<FingerprintRecord> {
    private static final long serialVersionUID = 1L;
    private final Destination dest;
    private final String storageClass;
    private final List<MetadataPair> userMetadata;
    private final String selregion;
    private final boolean produced;

    public S3UploadCallable(boolean produced, String accessKey, Secret secretKey, Destination dest, List<MetadataPair> userMetadata, String storageClass,
            String selregion) {
        super(accessKey, secretKey);
        this.dest = dest;
        this.storageClass = storageClass;
        this.userMetadata = userMetadata;
        this.selregion = selregion;
        this.produced = produced;
    }

    public ObjectMetadata buildMetadata(FilePath filePath) throws IOException, InterruptedException {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(Mimetypes.getInstance().getMimetype(filePath.getName()));
        metadata.setContentLength(filePath.length());
        metadata.setLastModified(new Date(filePath.lastModified()));
        if ((storageClass != null) && !"".equals(storageClass)) {
            metadata.setHeader("x-amz-storage-class", storageClass);
        }
        for (MetadataPair metadataPair : userMetadata) {
            if (metadataPair.key.equalsIgnoreCase("Cache-Control")){
                metadata.setCacheControl(metadataPair.value);
            }else{
                metadata.addUserMetadata(metadataPair.key, metadataPair.value);
            }
        }
        return metadata;
    }

    /**
     * Upload from slave directly
     */
    public FingerprintRecord invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        return invoke(new FilePath(file));
    }

    /**
     * Stream from slave to master, then upload from master
     */
    public FingerprintRecord invoke(FilePath file) throws IOException, InterruptedException {
        setRegion();
        PutObjectResult result = getClient().putObject(dest.bucketName, dest.objectName, file.read(), buildMetadata(file));
        return new FingerprintRecord(produced, dest.bucketName, file.getName(), result.getETag());
    }

    private void setRegion() {
        Region region = RegionUtils.getRegion(Regions.valueOf(selregion).getName());
        getClient().setRegion(region);
    }
}
