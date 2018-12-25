package run_strategy;

import cmd_ops.CmdOperations;
import logging.Logger;

public class CompleteResetAndDumpStrategy implements RunStrategy {

    private CmdOperations cmdOperations = CmdOperations.getInstance();
    private Logger logger = Logger.getInstance();

    @Override
    public void execute() {

        logger.write("CompleteResetAndDumpStrategy chosen");

        cmdOperations.w3wpDump();
        cmdOperations.ecsDump();
        cmdOperations.restartECS();
        cmdOperations.restartIIS();

    }

}