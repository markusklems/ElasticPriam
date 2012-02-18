package com.netflix.priam.defaultimpl;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.common.collect.Lists;
import com.netflix.priam.IConfiguration;
import com.netflix.priam.ICredential;
import com.netflix.priam.utils.SystemUtils;

@Singleton
public class PriamConfiguration implements IConfiguration
{
    private static final String PRIAM_PRE = "priam";

    private static final String CONFIG_CASS_HOME_DIR = PRIAM_PRE + ".cass.home";
    private static final String CONFIG_CASS_START_SCRIPT = PRIAM_PRE + ".cass.startscript";
    private static final String CONFIG_CASS_STOP_SCRIPT = PRIAM_PRE + ".cass.stopscript";
    private static final String CONFIG_CLUSTER_NAME = PRIAM_PRE + ".clustername";
    private static final String CONFIG_LOAD_LOCAL_PROPERTIES = PRIAM_PRE + ".localbootstrap.enable";
    private static final String CONFIG_MAX_HEAP_SIZE = PRIAM_PRE + ".heap.size.";
    private static final String CONFIG_DATA_LOCATION = PRIAM_PRE + ".data.location";
    private static final String CONFIG_MR_ENABLE = PRIAM_PRE + ".multiregion.enable";
    private static final String CONFIG_CL_LOCATION = PRIAM_PRE + ".commitlog.location";
    private static final String CONFIG_JMX_LISTERN_PORT_NAME = PRIAM_PRE + ".jmx.port";
    private static final String CONFIG_AVAILABILITY_ZONES = PRIAM_PRE + ".zones.available";
    private static final String CONFIG_SAVE_CACHE_LOCATION = PRIAM_PRE + ".cache.location";
    private static final String CONFIG_NEW_MAX_HEAP_SIZE = PRIAM_PRE + ".heap.newgen.size.";
    private static final String CONFIG_DIRECT_MAX_HEAP_SIZE = PRIAM_PRE + ".direct.memory.size.";
    private static final String CONFIG_THRIFT_LISTERN_PORT_NAME = PRIAM_PRE + ".thrift.port";
    private static final String CONFIG_STORAGE_LISTERN_PORT_NAME = PRIAM_PRE + ".storage.port";
    private static final String CONFIG_CL_BK_LOCATION = PRIAM_PRE + ".backup.commitlog.location";
    private static final String CONFIG_THROTTLE_UPLOAD_PER_SECOND = PRIAM_PRE + ".upload.throttle";
    private static final String CONFIG_IN_MEMORY_COMPACTION_LIMIT = PRIAM_PRE + ".memory.compaction.limit";
    private static final String CONFIG_COMPACTION_THROUHPUT = PRIAM_PRE + ".compaction.throughput";
    private static final String CONFIG_BOOTCLUSTER_NAME = PRIAM_PRE + ".bootcluster";
    private static final String CONFIG_ENDPOINT_SNITCH = PRIAM_PRE + ".endpoint_snitch";

    // Backup and Restore
    private static final String CONFIG_BACKUP_THREADS = PRIAM_PRE + ".backup.threads";
    private static final String CONFIG_RESTORE_PREFIX = PRIAM_PRE + ".restore.prefix";
    private static final String CONFIG_INCR_BK_ENABLE = PRIAM_PRE + ".backup.incremental.enable";
    private static final String CONFIG_CL_BK_ENABLE = PRIAM_PRE + ".backup.commitlog.enable";
    private static final String CONFIG_AUTO_RESTORE_SNAPSHOTNAME = PRIAM_PRE + ".restore.snapshot";
    private static final String CONFIG_BUCKET_NAME = PRIAM_PRE + ".s3.bucket";
    private static final String CONFIG_BACKUP_HOUR = PRIAM_PRE + ".backup.hour";
    private static final String CONFIG_S3_BASE_DIR = PRIAM_PRE + ".s3.base_dir";
    private static final String CONFIG_RESTORE_THREADS = PRIAM_PRE + ".restore.threads";
    private static final String CONFIG_RESTORE_CLOSEST_TOKEN = PRIAM_PRE + ".restore.closesttoken";
    private static final String CONFIG_RESTORE_KEYSPACES = PRIAM_PRE + ".restore.keyspaces";
    private static final String CONFIG_BACKUP_CHUNK_SIZE = PRIAM_PRE + ".backup.chunksizemb";

    // Amazon specific
    private static final String CONFIG_ASG_NAME = PRIAM_PRE + ".az.asgname";
    private static final String CONFIG_REGION_NAME = PRIAM_PRE + ".az.region";
    private final String RAC = SystemUtils.getDataFromUrl("http://169.254.169.254/latest/meta-data/placement/availability-zone");
    private final String PUBLIC_HOSTNAME = SystemUtils.getDataFromUrl("http://169.254.169.254/latest/meta-data/public-hostname");
    private final String PUBLIC_IP = SystemUtils.getDataFromUrl("http://169.254.169.254/latest/meta-data/public-ipv4");
    private final String INSTANCE_ID = SystemUtils.getDataFromUrl("http://169.254.169.254/latest/meta-data/instance-id");
    private final String INSTANCE_TYPE = SystemUtils.getDataFromUrl("http://169.254.169.254/latest/meta-data/instance-type");
    private static String ASG_NAME = System.getenv("ASG_NAME");
    private static String REGION = System.getenv("EC2_REGION");

    // Defaults
    private final String DEFAULT_DATA_LOCATION = "/mnt/data/cassandra070/data";
    private final String DEFAULT_COMMIT_LOG_LOCATION = "/mnt/data/cassandra070/commitlog";
    private final String DEFAULT_COMMIT_LOG_BACKUP_LOCATION = "/mnt/data/backup/commitlog";
    private final String DEFAULT_CACHE_LOCATION = "/mnt/data/cassandra070/saved_caches";
    private final String DEFULT_ENDPOINT_SNITCH = "org.apache.cassandra.locator.Ec2Snitch";
    private final int DEFAULT_JMX_PORT = 7199;
    private final int DEFAULT_THRIFT_PORT = 9160;
    private final int DEFAULT_STORAGE_PORT = 7000;
    private final int DEFAULT_BACKUP_HOUR = 12;
    private final int DEFAULT_BACKUP_THREADS = 10;
    private final int DEFAULT_RESTORE_THREADS = 30;
    private final int DEFAULT_BACKUP_CHUNK_SIZE = 10;

    private PriamProperties config;

    private static class Attributes
    {
        public final static String APP_ID = "appId"; // ASG
        public final static String PROPERTY = "property";
        public final static String PROPERTY_VALUE = "value";
        public final static String REGION = "region";
    }

    private static final String DOMAIN = "PriamProperties";
    private static String ALL_QUERY = "select * from " + DOMAIN + " where " + Attributes.APP_ID + "='%s'";

    private final AmazonSimpleDBClient simpleDBClient;

    @Inject
    public PriamConfiguration(ICredential provider)
    {
        AWSCredentials cred = new BasicAWSCredentials(provider.getAccessKeyId(), provider.getSecretAccessKey());
        // End point is us-east-1
        simpleDBClient = new AmazonSimpleDBClient(cred);
        setupVars();
    }

    @Override
    public void intialize()
    {
        populateProps();
        SystemUtils.createDirs(getBackupCommitLogLocation());
        SystemUtils.createDirs(getCommitLogLocation());
        SystemUtils.createDirs(getCacheLocation());
        SystemUtils.createDirs(getDataFileLocation());
    }

    private void setupVars(){
        //Search in java opt properties
        ASG_NAME = StringUtils.isBlank(ASG_NAME)?System.getProperty("ASG_NAME"):ASG_NAME;
        REGION = StringUtils.isBlank(REGION)?System.getProperty("EC2_REGION"):REGION;
        if (StringUtils.isBlank(REGION))
            REGION = "us-east-1";
        
    }

    private void populateProps()
    {
        String nextToken = null;
        String appid = ASG_NAME.substring(0, ASG_NAME.lastIndexOf('-'));
        do
        {
            SelectRequest request = new SelectRequest(String.format(ALL_QUERY, appid));
            request.setNextToken(nextToken);
            SelectResult result = simpleDBClient.select(request);
            nextToken = result.getNextToken();
            Iterator<Item> itemiter = result.getItems().iterator();
            while (itemiter.hasNext())
                addProperty(itemiter.next());

        } while (nextToken != null);

    }

    private void addProperty(Item item)
    {
        Iterator<Attribute> attrs = item.getAttributes().iterator();
        String prop = "";
        String value = "";
        String dc = "";
        while (attrs.hasNext())
        {
            Attribute att = attrs.next();
            if (att.getName().equals(Attributes.PROPERTY))
                prop = att.getValue();
            else if (att.getName().equals(Attributes.PROPERTY_VALUE))
                value = att.getValue();
            else if (att.getName().equals(Attributes.REGION))
                dc = att.getValue();
        }
        // Ignore, if not this region
        if (StringUtils.isNotBlank(dc) && !dc.equals(REGION))
            return;
        // Override only if region is specified
        if (config.contains(prop) && StringUtils.isBlank(dc))
            return;
        config.put(prop, value);
    }

    @Override
    public String getCassStartupScript()
    {
        return config.getProperty(CONFIG_CASS_START_SCRIPT);
    }

    @Override
    public String getCassStopScript()
    {
        return config.getProperty(CONFIG_CASS_STOP_SCRIPT);
    }

    @Override
    public String getCassHome()
    {
        return config.getProperty(CONFIG_CASS_HOME_DIR);
    }

    @Override
    public String getBackupLocation()
    {
        return config.getProperty(CONFIG_S3_BASE_DIR);
    }

    @Override
    public String getBackupPrefix()
    {
        return config.getProperty(CONFIG_BUCKET_NAME);
    }

    @Override
    public String getRestorePrefix()
    {
        return config.getProperty(CONFIG_RESTORE_PREFIX);
    }

    @Override
    public List<String> getRestoreKeySpaces()
    {
        return config.getList(CONFIG_RESTORE_KEYSPACES);
    }

    @Override
    public String getDataFileLocation()
    {
        return config.getProperty(CONFIG_DATA_LOCATION, DEFAULT_DATA_LOCATION);
    }

    @Override
    public String getCacheLocation()
    {
        return config.getProperty(CONFIG_SAVE_CACHE_LOCATION, DEFAULT_CACHE_LOCATION);
    }

    @Override
    public String getCommitLogLocation()
    {
        return config.getProperty(CONFIG_CL_LOCATION, DEFAULT_COMMIT_LOG_LOCATION);
    }

    @Override
    public String getBackupCommitLogLocation()
    {
        return config.getProperty(CONFIG_CL_BK_LOCATION, DEFAULT_COMMIT_LOG_BACKUP_LOCATION);
    }

    @Override
    public long getBackupChunkSize()
    {
        return config.getLong(CONFIG_BACKUP_CHUNK_SIZE, DEFAULT_BACKUP_CHUNK_SIZE);
    }

    @Override
    public boolean isCommitLogBackup()
    {
        return config.getBoolean(CONFIG_CL_BK_ENABLE, false);
    }

    @Override
    public int getJmxPort()
    {
        return config.getInteger(CONFIG_JMX_LISTERN_PORT_NAME, DEFAULT_JMX_PORT);
    }

    @Override
    public int getThriftPort()
    {
        return config.getInteger(CONFIG_THRIFT_LISTERN_PORT_NAME, DEFAULT_THRIFT_PORT);
    }

    @Override
    public int getStoragePort()
    {
        return config.getInteger(CONFIG_STORAGE_LISTERN_PORT_NAME, DEFAULT_STORAGE_PORT);
    }

    @Override
    public String getSnitch()
    {
        return config.getProperty(CONFIG_ENDPOINT_SNITCH, DEFULT_ENDPOINT_SNITCH);
    }

    @Override
    public String getAppName()
    {
        return config.getProperty(CONFIG_CLUSTER_NAME);
    }

    @Override
    public String getRac()
    {
        return RAC;
    }

    @Override
    public List<String> getRacs()
    {
        return config.getList(CONFIG_AVAILABILITY_ZONES);
    }

    @Override
    public String getHostname()
    {
        return PUBLIC_HOSTNAME;
    }

    @Override
    public String getInstanceName()
    {
        return INSTANCE_ID;
    }

    @Override
    public String getHeapSize()
    {
        return config.getProperty(CONFIG_MAX_HEAP_SIZE + INSTANCE_TYPE, "2G");
    }

    @Override
    public String getHeapNewSize()
    {
        return config.getProperty(CONFIG_NEW_MAX_HEAP_SIZE + INSTANCE_TYPE, "2G");
    }

    @Override
    public int getBackupHour()
    {
        return config.getInteger(CONFIG_BACKUP_HOUR, DEFAULT_BACKUP_HOUR);
    }

    @Override
    public String getRestoreSnapshot()
    {
        return config.getProperty(CONFIG_AUTO_RESTORE_SNAPSHOTNAME, "");
    }

    @Override
    public String getDC()
    {
        return config.getProperty(CONFIG_REGION_NAME, "");
    }

    @Override
    public void setDC(String region)
    {
        config.setProperty(CONFIG_REGION_NAME, region);
    }

    @Override
    public boolean isMultiDC()
    {
        return config.getBoolean(CONFIG_MR_ENABLE, false);
    }

    @Override
    public int getMaxBackupUploadThreads()
    {

        return config.getInteger(CONFIG_BACKUP_THREADS, DEFAULT_BACKUP_THREADS);
    }

    @Override
    public int getMaxBackupDownloadThreads()
    {
        return config.getInteger(CONFIG_RESTORE_THREADS, DEFAULT_RESTORE_THREADS);
    }

    @Override
    public boolean isRestoreClosestToken()
    {
        return config.getBoolean(CONFIG_RESTORE_CLOSEST_TOKEN, false);
    }

    @Override
    public String getASGName()
    {
        return config.getProperty(CONFIG_ASG_NAME, "");
    }

    @Override
    public boolean isIncrBackup()
    {
        return config.getBoolean(CONFIG_INCR_BK_ENABLE, true);
    }

    @Override
    public String getHostIP()
    {
        return PUBLIC_IP;
    }

    @Override
    public int getUploadThrottle()
    {
        return config.getInteger(CONFIG_THROTTLE_UPLOAD_PER_SECOND, Integer.MAX_VALUE);
    }

    @Override
    public boolean isLocalBootstrapEnabled()
    {
        return config.getBoolean(CONFIG_LOAD_LOCAL_PROPERTIES, false);
    }

    @Override
    public int getInMemoryCompactionLimit()
    {
        return config.getInteger(CONFIG_IN_MEMORY_COMPACTION_LIMIT, 128);
    }

    @Override
    public int getCompactionThroughput()
    {
        return config.getInteger(CONFIG_COMPACTION_THROUHPUT, 8);
    }

    @Override
    public String getMaxDirectMemory()
    {
        return config.getProperty(CONFIG_DIRECT_MAX_HEAP_SIZE + INSTANCE_TYPE, "50G");
    }

    @Override
    public String getBootClusterName()
    {
        return config.getProperty(CONFIG_BOOTCLUSTER_NAME, "cass_turtle");
    }

    private class PriamProperties extends Properties
    {

        private static final long serialVersionUID = 1L;

        public int getInteger(String prop, int defaultValue)
        {
            return getProperty(prop) == null ? defaultValue : Integer.parseInt(getProperty(prop));
        }

        public long getLong(String prop, long defaultValue)
        {
            return getProperty(prop) == null ? defaultValue : Long.parseLong(getProperty(prop));
        }

        public boolean getBoolean(String prop, boolean defaultValue)
        {
            return getProperty(prop) == null ? defaultValue : Boolean.parseBoolean(getProperty(prop));
        }

        public List<String> getList(String prop)
        {
            if (getProperty(prop) == null)
                return Lists.newArrayList();
            return Arrays.asList(getProperty(prop).split(","));
        }

    }

}
