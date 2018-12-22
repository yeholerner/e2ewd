package file_ops;

import logging.Logger;
import sun.rmi.runtime.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class ConfigFile {

    private static ConfigFile configFileInstance = new ConfigFile();
    private Logger logger = Logger.getInstance();
    private String token;
    private String host;
    private String protocol;
    private boolean restartECS;
    private int port;

    private ConfigFile(){
        Properties properties = new Properties();
        InputStream input;

        try {
            input = new FileInputStream(executionPath() + "/config.properties");
            properties.load(input);

            setToken(properties.getProperty("token"));
            setHost(properties.getProperty("host"));
            setPort(Integer.parseInt(properties.getProperty("port")));
            setProtocol(properties.getProperty("protocol"));
            setRestartECS(Boolean.parseBoolean(properties.getProperty("restartECS")));

        } catch (IOException e) {
            logger.write("ERROR: reading configuration file - " + e.getMessage());
        }
    }

    public static ConfigFile getInstance(){
        if (configFileInstance == null){
            configFileInstance = new ConfigFile();
        }
        return configFileInstance;
    }

    private  String executionPath(){
        String jarLocation = null;
        try {
            jarLocation = new File(ConfigFile.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getCanonicalPath();
            Path path = Paths.get(jarLocation);
            return String.valueOf(path.getParent());
        } catch (IOException | URISyntaxException e) {
            logger.write("ERROR: " + e.getMessage());
        }
        return jarLocation;
    }

    private void setToken(String token) {
        this.token = token;
    }

    private void setHost(String host) {
        this.host = host;
    }

    private void setPort(int port) {
        this.port = port;
    }

    public int getPort(){
        return this.port;
    }

    public String getHost(){
        return this.host;
    }

    public String getToken(){
        return this.token;
    }

    public boolean isRestartECS() {
        return restartECS;
    }

    private void setRestartECS(boolean restartECS) {
        this.restartECS = restartECS;
    }

    public String getProtocol() {
        return protocol;
    }

    private void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public boolean isConfigFileValid(){
        HashMap<String, String> configMap = new HashMap<>(5);
        configMap.put("token", token);
        configMap.put("host", host);
        configMap.put("protocol", protocol);
        configMap.put("port", String.valueOf(port));
        configMap.put("restartECS", String.valueOf(restartECS));

        Set set = configMap.entrySet();
        Iterator iterator = set.iterator();

        while (iterator.hasNext()){
            Map.Entry mapEntry = (Map.Entry) iterator.next();
            if (String.valueOf(mapEntry.getValue()).isEmpty()){
                logger.write(mapEntry.getKey() + " is empty.");
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "ConfigFile{\n\t" +
                "token=" + token + ",\n\t" +
                "host=" + host + ",\n\t" +
                "port=" + port + ",\n\t" +
                "protocol=" + protocol + ",\n\t" +
                "restartECS=" + restartECS + "\n\t" +
                "}";
    }


}
