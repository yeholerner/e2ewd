package cmd_ops;

import com.profesorfalken.jpowershell.PowerShell;
import integrations.SlackClient;
import logging.Logger;
import models.ElastiCube;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CmdOperations {

    private static CmdOperations instance;
    private final Runtime runtime = Runtime.getRuntime();
    private final org.slf4j.Logger logger = LoggerFactory.getLogger(CmdOperations.class);
    private final String procdumpPath = executionPath() + "\\procdump\\procdump.exe";
    private final int PROCESS_TIMEOUT = 15;

    private CmdOperations(){

    }

    public static CmdOperations getInstance(){

        if (instance == null){
            instance = new CmdOperations();
        }

        return instance;
    }

    public List<ElastiCube> getListElastiCubes(){
        List<ElastiCube> elasticubes = new ArrayList<>();

        try {
            String[] psmCmd = new String[]{
                    "cmd.exe",
                    "/c",
                    "C:\\Program Files\\Sisense\\Prism\\Psm.exe",
                    "ecs",
                    "ListCubes",
                    "serverAddress=localhost"};

            String[] environmentalVariable = { "SISENSE_PSM=true" };

            Process listCubesCommand = runtime.exec(psmCmd, environmentalVariable);
            logger.debug("Executing " + Arrays.toString(environmentalVariable) + "&&" + Arrays.toString(psmCmd));

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(listCubesCommand.getInputStream()));
            BufferedReader errorInput = new BufferedReader(new InputStreamReader(listCubesCommand.getErrorStream()));

            Pattern listCubesPattern = Pattern.compile("Cube Name \\[(.*?)] ID : \\[(.*?)] FarmPath \\[(.*?)] Status \\[(.*?)]");
            Pattern errorPattern = Pattern.compile("\\((.*?)\\)");

            // Read stdout
            String s;
            logger.debug("Output stream:");
            while ((s = stdInput.readLine()) != null) {
                logger.debug(s);

                if (s.startsWith("Cube Name")){
                    Matcher cubeNameMatcher = listCubesPattern.matcher(s);
                    while (cubeNameMatcher.find()){

                        ElastiCube elastiCube = new ElastiCube(cubeNameMatcher.group(1), cubeNameMatcher.group(4));
                        setElastiCubeProperties(elastiCube);

                        // filter out all non running ElastiCubes
                        if (elastiCube.getState().equals("RUNNING") && !elastiCube.isLocked()){
                            if (elasticubes != null) {
                                elasticubes.add(elastiCube);
                                logger.debug("Found ElastiCube " + elastiCube.getName());
                            }
                        }

                    }
                } else {
                    Matcher m = errorPattern.matcher(s);
                    while (m.find()){

                        logger.error("Command " + Arrays.toString(psmCmd) + "returned an error: " + m.group(1));
                        if (m.group(1).equals("the server, 'localhost', is not responding.")){
                            elasticubes = null;
                        }

                    }
                }
            }

            // Read stderr
            String e;
            logger.debug("Error stream: ");
            while ((e = errorInput.readLine()) != null){
                    logger.error(e);
                }

            // Check for timeout
            if (!listCubesCommand.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS)){

                logger.error("Operation timed out (" + PROCESS_TIMEOUT + "s). Destroying process...");
                listCubesCommand.destroyForcibly();
            }

        } catch (IOException | InterruptedException e) {
            logger.error(e.getMessage());
        }

        return elasticubes;
    }

    private void setElastiCubeProperties(ElastiCube elastiCube) throws IOException, InterruptedException {

        String[] psmCmd = new String[]{
                "C:\\Program Files\\Sisense\\Prism\\Psm.exe",
                "ecube",
                "info",
                "name=" + elastiCube.getName(),
                "serverAddress=localhost"};

        Process ecubePortCommand = Runtime.getRuntime().exec(psmCmd);
        logger.debug("Running command " + Arrays.toString(psmCmd));

        BufferedReader stdInput = new BufferedReader(new InputStreamReader(ecubePortCommand.getInputStream()));
        BufferedReader errorStream = new BufferedReader(new InputStreamReader(ecubePortCommand.getErrorStream()));

        // read stdin
        String s;
        logger.debug("Output stream:");
        while ((s = stdInput.readLine()) != null){
            logger.debug(s);
            if (s.startsWith("Port")){
                int port = Integer.parseInt(s.split("Port: ")[1]);
                elastiCube.setPort(port);
            } else if (s.startsWith("IsLocked")){
                boolean locked = Boolean.valueOf(s.split("IsLocked: ")[1]);
                elastiCube.setLocked(locked);
            }
        }

        // Read stderr
        String e;
        logger.debug("Error stream:");
        while ((e = errorStream.readLine()) != null){
            logger.error(e);
        }

        // Check that process hasn't timed out
        if (!ecubePortCommand.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS)){
            logger.error("Operation timed out ( + PROCESS_TIMEOUT + s). Destroying process...");
            ecubePortCommand.destroyForcibly();
        }
    }

    public boolean isMonetDBQuerySuccessful(ElastiCube elastiCube) throws IOException, InterruptedException {

        boolean success = false;

        String[] command = new String[]{
                "cmd.exe",
                "/c",
                "mclient.exe",
                "-p" + elastiCube.getPort(),
                "-fcsv",
                "-s",
                "\"SELECT 1\""};

        Process monetDBQueryCmd = runtime.exec(command, null, new File(executionPath() + "\\mclient\\"));
        logger.debug("Executing command " + Arrays.toString(command));

        BufferedReader stdInput = new BufferedReader(new InputStreamReader(monetDBQueryCmd.getInputStream()));
        BufferedReader errorStream = new BufferedReader(new InputStreamReader(monetDBQueryCmd.getErrorStream()));

        // Read stdin
        String s;
        while ((s = stdInput.readLine()) != null) {
            logger.debug("Output stream: " + s);
            try {
                if (Integer.parseInt(s) == 1){
                    success = true;
                }

            } catch (NumberFormatException e){

                logger.error(e.getMessage());
                return success;

            }

        }


        // Read stderr
        String e;
        while ((e = errorStream.readLine()) != null){
            logger.error("Error stream: " + e);
        }

        // Check for process timeout
        if(!monetDBQueryCmd.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS)){
            logger.error("Operation timed out (" + PROCESS_TIMEOUT + "s). Destroying process...");
            monetDBQueryCmd.destroyForcibly();
            return false;
        }

        return success;
    }

    public int getMonetDBConcurrentConnections(ElastiCube elastiCube) throws IOException, InterruptedException {

        int numberOfConnections = 0;

        String[] command = new String[]{
                "cmd.exe",
                "/c",
                "mclient.exe",
                "-p" + elastiCube.getPort(),
                "-fcsv",
                "-lmal",
                "-s",
                "\"c := status.count_clients();io.print(c);\""};

        Process monetDBQueryCmd = runtime.exec(command, null, new File(executionPath() + "\\mclient\\"));
        logger.debug("Executing command " + Arrays.toString(command));

        BufferedReader stdInput = new BufferedReader(new InputStreamReader(monetDBQueryCmd.getInputStream()));
        BufferedReader errorStream = new BufferedReader(new InputStreamReader(monetDBQueryCmd.getErrorStream()));

        // Read stdin
        String s;
        while ((s = stdInput.readLine()) != null) {
            logger.debug("Output stream: " + s);

            try{
                // subtracting 1 because the test creates a connection
                numberOfConnections = Integer.parseInt(s) -1 ;

            } catch (NumberFormatException e){
                logger.error("Error parsing command output as integer.");
                numberOfConnections = 0;
            }
        }


        // Read stderr
        String e;
        while ((e = errorStream.readLine()) != null){
            logger.error("Error stream: " + e);
        }

        // Check for process timeout
        if(!monetDBQueryCmd.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS)){
            logger.error("Operation timed out (" + PROCESS_TIMEOUT + "s). Destroying process...");
            monetDBQueryCmd.destroyForcibly();
        }

        return numberOfConnections;
    }

    public String getSisenseVersion() throws IOException, InterruptedException {

        String[] command = new String[]{
                "reg",
                "QUERY",
                "HKEY_LOCAL_MACHINE\\SOFTWARE\\Sisense\\ECS",
                "/v",
                "Version"
        };

        Process process = Runtime.getRuntime().exec(command);

        StringWriter stringWriter = new StringWriter();

        logger.debug("Output stream:");
        try (InputStream inputStream = process.getInputStream()){

            int c;
            while ((c = inputStream.read()) != -1){
                stringWriter.write(c);
            }
            logger.debug(stringWriter.toString());
        }


        try (InputStream errorStream = process.getErrorStream()){
            logger.debug("Error stream:");
            int e;
            while ((e = errorStream.read()) != -1){
                stringWriter.write(e);
                logger.error(stringWriter.toString());
            }
        }

        // Check if operation timed out
        if (!process.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS)){
            logger.error("Operation timed out (" + PROCESS_TIMEOUT + "s.)");
            process.destroyForcibly();
            return "CANNOT DETECT";
        }
        return stringWriter.toString().split("   ")[3].trim();

    }

    // TODO add indication to mongo and Slack about dump occurrance
    public void w3wpDump() throws IOException, InterruptedException {

        String command = procdumpPath + " -accepteula -o -ma w3wp iis_dump.dmp";

        Process process = runtime.exec(command);
        logger.info("Creating IIS memory dump...");

        BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        // Read stdout
        String s;
        while ((s = stdInput.readLine()) != null){
            logger.debug("Output stream: " + s);
        }

        // Read stderr
        String e;
        while ((e = errorStream.readLine()) != null){
            logger.error("Error stream: " + e);
        }

        // check that process hasn't timed out
        if (!process.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS)){
            logger.error("Operation timed out (" + PROCESS_TIMEOUT + " s.) Destroying process...");
            process.destroyForcibly();
        } else {
            logger.info("Operation successful");
        }


    }

    // TODO add indication to mongo and Slack about dump occurrance
    public void ecsDump() throws IOException, InterruptedException {

        String command = procdumpPath + " -accepteula -o -ma ElastiCube.ManagementService ecs_dump.dmp";
        logger.info("Creating ECS memory dump...");

        Process process = runtime.exec(command);

        BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        // Read stdout
        String s;
        while ((s = stdInput.readLine()) != null){
            logger.debug("Output stream: " + s);
        }

        // Read stderr
        String e;
        while ((e = errorStream.readLine()) != null){
            logger.error("Error stream: " + e);
        }

        // check that process hasn't timed out
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            logger.error("Operation timed out (" + PROCESS_TIMEOUT + " s.) Destroying process...");
            process.destroyForcibly();
        }
        else {
            logger.info("Operation successful");
        }

    }

    public void ecDump(ElastiCube elastiCube){

        logger.info("Creating ElastiCube " + elastiCube.getName() + " process memory dump...");
        int ecPort = elastiCube.getPort();

        String output = PowerShell.executeSingleCommand("Get-WmiObject Win32_Process -Filter \"name = 'elasticube.exe'\" | Select-Object ProcessId, CommandLine | where CommandLine -CMatch \"mapi_port=" + ecPort + "\" | Select-Object ProcessId -ExpandProperty ProcessId").getCommandOutput();
        logger.info("Output " + output);

        Process process;
        try {
            int pid = Integer.parseInt(output);
            logger.debug("PID: " + pid);

            String command = procdumpPath + " -accepteula -o -ma " + pid + " " + elastiCube.getName() + ".dmp";

            process = runtime.exec(command);

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            // Read stdout
            String s;
            while ((s = stdInput.readLine()) != null){
                logger.debug("Output stream: " + s);
            }

            // Read stderr
            String e;
            while ((e = errorStream.readLine()) != null){
                logger.error("Error stream: " + e);
            }

            // check that process hasn't timed out
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                logger.error("Operation timed out (" + PROCESS_TIMEOUT + " s.) Destroying process...");
                process.destroyForcibly();
            }
            else {
                logger.info("Operation successful");
            }



        } catch (NumberFormatException e){
            logger.error("Unable to parse output as integer: " + e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            logger.error("Unable to execute process - " + e.getMessage());
        }
    }

    public void restartECS() throws IOException, InterruptedException {

        logger.info("Restarting ECS...");

        String serviceName;
        if (getSisenseVersion().startsWith("6") || getSisenseVersion().startsWith("7.1") || getSisenseVersion().startsWith("7.0")){
            serviceName = "ElastiCubeManagmentService";
        }
        else {
            serviceName = "Sisense.ECMS";
        }

        String restartCommand = "powershell.exe Restart-Service -DisplayName " + serviceName + " -Force";
        logger.debug("Running command " + restartCommand);

        Process psProcess = runtime.exec(restartCommand);

        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(psProcess.getInputStream()))) {
            while ((line = reader.readLine()) != null){
                logger.debug("Output stream " + line);
            }
        }

        String error;
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(psProcess.getErrorStream()))) {
            while ((error = errorReader.readLine()) != null){
                logger.error(error);
            }
        }

        // check that process hasn't timed out
        if (!psProcess.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS)){
                logger.error("Operation timed out (" + PROCESS_TIMEOUT + " s.) Destroying process...");
                psProcess.destroyForcibly();
        } else {
            logger.info("ECS restarted.");
            SlackClient.getInstance().sendMessage(":recycle: ECS restarted.");
        }

    }

    public void restartIIS() throws IOException, InterruptedException {

        logger.info("Running IIS reset...");
        String restartCommand = "iisreset";


        Process psProcess = runtime.exec(restartCommand);
        logger.debug("Running command " + restartCommand);

        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(psProcess.getInputStream()))) {
                while ((line = reader.readLine()) != null){
                    logger.debug("Output stream: " + line);
                }
            }

        String error;
        try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(psProcess.getErrorStream()))) {
                while ((error = errorReader.readLine()) != null){
                    logger.error("Error stream: " + error);
                }
            }

        // check that operation hasn't timed out
        if (!psProcess.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS)){
            logger.error("Operation timed out (" + PROCESS_TIMEOUT + " s.) Destroying process...");
            psProcess.destroyForcibly();
        }
        else {
            logger.info("IIS restarted.");
            SlackClient.getInstance().sendMessage(":recycle: IIS restarted.");
        }

    }

    public String getHostname() throws IOException, InterruptedException {

        String hostname = "";
        String cmd = "hostname";

        Process getHostnameProcess = runtime.exec(cmd);

        // read stdout
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(getHostnameProcess.getInputStream()))){
            // read stdout
            String s;
            while ((s = reader.readLine()) != null){
                logger.debug("Output stream: " + s);
                hostname = s;
            }
        }

        // read stderr
        try(BufferedReader errorReader = new BufferedReader(new InputStreamReader(getHostnameProcess.getErrorStream()))){

            String e;
            while ((e = errorReader.readLine()) != null){
                logger.debug("Error stream: " + e);
            }
        }

        if (!getHostnameProcess.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS)){
            logger.error("Operation timed out (" + PROCESS_TIMEOUT + " s.) Destroying process...");
            getHostnameProcess.destroyForcibly();
            return "";
        } else {
            logger.debug("Hostname retrieved successfully");
        }

        return hostname;

    }

    private String executionPath(){
        String jarLocation = null;
        try {
            jarLocation = new File(Logger.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getCanonicalPath();
            Path path = Paths.get(jarLocation);
            return String.valueOf(path.getParent());
        } catch (IOException | URISyntaxException e) {
            System.out.println(e.getMessage());
        }
        return jarLocation;
    }
}