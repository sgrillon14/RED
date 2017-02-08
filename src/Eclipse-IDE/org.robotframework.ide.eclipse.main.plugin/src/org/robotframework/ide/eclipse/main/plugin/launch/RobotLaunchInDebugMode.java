/*
 * Copyright 2017 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.launch;

import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IProcess;
import org.rf.ide.core.executor.RobotRuntimeEnvironment;
import org.rf.ide.core.executor.RunCommandLineCallBuilder.RunCommandLine;
import org.robotframework.ide.eclipse.main.plugin.debug.model.RobotDebugTarget;
import org.robotframework.ide.eclipse.main.plugin.debug.utils.DebugSocketManager;
import org.robotframework.ide.eclipse.main.plugin.model.RobotProject;

public class RobotLaunchInDebugMode extends RobotLaunchInMode {

    private final RobotEventBroker robotEventBroker;

    public RobotLaunchInDebugMode(final RobotEventBroker robotEventBroker) {
        this.robotEventBroker = robotEventBroker;
    }

    @Override
    protected Process launchAndAttachToProcess(final RobotLaunchConfiguration robotConfig, final ILaunch launch,
            final IProgressMonitor monitor) throws CoreException, IOException {

        final RobotProject robotProject = robotConfig.getRobotProject();

        final RunCommandLine cmdLine = prepareCommandLineBuilder(robotConfig).enableDebug(true).build();
        if (cmdLine.getPort() < 0) {
            throw newCoreException("Unable to find free port");
        }

        final RobotRuntimeEnvironment runtimeEnvironment = getRobotRuntimeEnvironment(robotProject);

        final DebugSocketManager socketManager = new DebugSocketManager("localhost", cmdLine.getPort());
        new Thread(socketManager).start();
        socketManager.waitForDebugServerSocket();

        final String description = robotConfig.createConsoleDescription(runtimeEnvironment);
        final String version = robotConfig.createExecutorVersion(runtimeEnvironment);

        final Process process = execProcess(cmdLine, robotConfig);
        final IProcess eclipseProcess = DebugPlugin.newProcess(launch, process, description);

        final RobotConsoleFacade consoleFacade = new RobotConsoleFacade();
        consoleFacade.connect(robotConfig, runtimeEnvironment, cmdLine, version);

        try {
            final RobotDebugTarget target = new RobotDebugTarget(launch, eclipseProcess, consoleFacade, false);
            target.connect(robotConfig.getResourcesUnderDebug(), robotEventBroker, socketManager);
            launch.addDebugTarget(target);
        } catch (final CoreException e) {
            socketManager.closeServerSocket();
        }

        return process;
    }
}