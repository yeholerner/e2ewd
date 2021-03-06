package run_strategy;

import cmd_ops.CmdOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class IISDumpStrategy implements RunStrategy {

    private CmdOperations cmdOperations = CmdOperations.getInstance();
    private final Logger logger = LoggerFactory.getLogger(IISDumpStrategy.class);

    @Override
    public void execute() {

        logger.info("Executing IISDumpStrategy...");

        try {
            cmdOperations.w3wpDump();
        } catch (IOException | InterruptedException e) {
            logger.error("Error creating IIS memory dump: " + e.getMessage());
        }

    }
}
