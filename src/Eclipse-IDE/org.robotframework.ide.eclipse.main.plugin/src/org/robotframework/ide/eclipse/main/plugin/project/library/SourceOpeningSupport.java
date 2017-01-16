/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.project.library;

import java.io.File;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.statushandlers.StatusManager;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.rf.ide.core.dryrun.RobotDryRunKeywordSource;
import org.rf.ide.core.executor.RobotRuntimeEnvironment;
import org.robotframework.ide.eclipse.main.plugin.RedPlugin;
import org.robotframework.ide.eclipse.main.plugin.model.LibspecsFolder;
import org.robotframework.ide.eclipse.main.plugin.model.RobotModel;
import org.robotframework.ide.eclipse.main.plugin.model.RobotProject;
import org.robotframework.ide.eclipse.main.plugin.project.KeywordsAutoDiscoverer;

import com.google.common.base.Optional;

/**
 * @author bembenek
 */
public class SourceOpeningSupport {

    public static void open(final IWorkbenchPage page, final RobotModel model, final IProject project,
            final LibrarySpecification libSpec) {
        try {
            final IPath location = extractLibraryLocation(model, project, libSpec);
            final IFile file = resolveFile(location, project, libSpec);
            openInEditor(page, file);
        } catch (final CoreException e) {
            handleOpeningError(libSpec, e);
        }
    }

    public static void open(final IWorkbenchPage page, final RobotModel model, final IProject project,
            final LibrarySpecification libSpec, final KeywordSpecification kwSpec) {
        final Optional<RobotDryRunKeywordSource> kwSource = tryToFindKeywordSource(model, project, libSpec, kwSpec);
        if (kwSource.isPresent()) {
            try {
                final IPath location = new Path(kwSource.get().getFilePath());
                final IFile file = resolveFile(location, project, libSpec);
                final IEditorPart editor = openInEditor(page, file);
                if (editor instanceof TextEditor) {
                    selectLine((TextEditor) editor, kwSource.get().getLineNumber() - 1);
                }
            } catch (final CoreException e) {
                handleOpeningError(libSpec, e);
            }
        } else {
            open(page, model, project, libSpec);
        }
    }

    private static Optional<RobotDryRunKeywordSource> tryToFindKeywordSource(final RobotModel model,
            final IProject project, final LibrarySpecification libSpec, final KeywordSpecification kwSpec) {
        final RobotProject robotProject = model.createRobotProject(project);
        final String qualifiedKwName = libSpec.getName() + "." + kwSpec.getName();
        Optional<RobotDryRunKeywordSource> kwSource = robotProject.getKeywordSource(qualifiedKwName);
        if (!kwSource.isPresent()) {
            new KeywordsAutoDiscoverer(robotProject).start();
            kwSource = robotProject.getKeywordSource(qualifiedKwName);
        }
        return kwSource;
    }

    private static IFile resolveFile(final IPath location, final IProject project, final LibrarySpecification libSpec)
            throws CoreException {
        if (location == null) {
            throw new CoreException(new Status(IStatus.ERROR, RedPlugin.PLUGIN_ID, "Empty location path!"));
        }
        IFile file = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(location);
        if (file == null || !file.isAccessible()) {
            final String libName = libSpec.getName() + ".py";
            file = LibspecsFolder.createIfNeeded(project).getFile(libName);
            file.createLink(location, IResource.REPLACE | IResource.HIDDEN, null);
        }
        return file;
    }

    private static IEditorPart openInEditor(final IWorkbenchPage page, final IFile file) throws PartInitException {
        IEditorDescriptor desc = IDE.getEditorDescriptor(file);
        if (!desc.isInternal()) {
            // we don't want to open files with external editors (e.g. running script files etc),
            // so if there is no internal editor, then we will use default text editor
            final IEditorRegistry editorRegistry = PlatformUI.getWorkbench().getEditorRegistry();
            desc = editorRegistry.findEditor(EditorsUI.DEFAULT_TEXT_EDITOR_ID);
            if (desc == null) {
                throw new EditorOpeningException("No suitable editor for file: " + file.getName());
            }
        }
        return page.openEditor(new FileEditorInput(file), desc.getId());
    }

    public static void tryToOpenInEditor(final IWorkbenchPage page, final IFile file) {
        try {
            openInEditor(page, file);
        } catch (final PartInitException e) {
            throw new EditorOpeningException("Unable to open editor for file: " + file.getName(), e);
        }
    }

    private static void handleOpeningError(final LibrarySpecification libSpec, final Throwable cause) {
        final String message = "Unable to open editor for library:\n" + libSpec.getName();
        final Status status = new Status(IStatus.ERROR, RedPlugin.PLUGIN_ID, message, cause);
        StatusManager.getManager().handle(status, StatusManager.SHOW);
    }

    private static void selectLine(final TextEditor editor, final int line) {
        try {
            final IDocumentProvider documentProvider = editor.getDocumentProvider();
            final IDocument document = documentProvider.getDocument(editor.getEditorInput());
            final IRegion lineInformation = document.getLineInformation(line);
            final TextSelection selection = new TextSelection(lineInformation.getOffset(), lineInformation.getLength());
            editor.getSelectionProvider().setSelection(selection);
        } catch (final BadLocationException e) {
            throw new LineSelectionException("Unable to select line: " + line, e);
        }
    }

    public static IPath extractLibraryLocation(final RobotModel model, final IProject project,
            final LibrarySpecification libSpec) {
        final RobotProject robotProject = model.createRobotProject(project);
        if (robotProject.isStandardLibrary(libSpec)) {
            final RobotRuntimeEnvironment runtimeEnvironment = robotProject.getRuntimeEnvironment();
            final File standardLibraryPath = runtimeEnvironment.getStandardLibraryPath(libSpec.getName());
            return standardLibraryPath == null ? null : new Path(standardLibraryPath.getAbsolutePath());
        } else if (robotProject.isReferencedLibrary(libSpec)) {
            final IPath pythonLibPath = new Path(robotProject.getPythonLibraryPath(libSpec.getName()));
            if (pythonLibPath.toFile().exists()) {
                return pythonLibPath;
            } else if (libSpec.getName().contains(".")) {
                final IPath path = tryToFindLibWithoutQualifiedPart(pythonLibPath);
                if (path != null) {
                    return path;
                }
            }
            return findModuleLibrary(pythonLibPath, libSpec.getName());
        }

        return null;
    }

    private static IPath tryToFindLibWithoutQualifiedPart(final IPath pythonLibPath) {
        final String fileExt = pythonLibPath.getFileExtension();
        final String lastSegment = pythonLibPath.removeFileExtension().lastSegment();
        final String withoutDot = lastSegment.substring(0, lastSegment.lastIndexOf('.'));

        final IPath resultPath = pythonLibPath.removeLastSegments(1).append(withoutDot).addFileExtension(fileExt);
        return resultPath.toFile().exists() ? resultPath : null;
    }

    private static IPath findModuleLibrary(final IPath pythonLibPath, final String libName) {
        final IPath pathToInitFile = pythonLibPath.removeLastSegments(1).append(libName).append("__init__.py");
        return pathToInitFile.toFile().exists() ? pathToInitFile : null;
    }

    private static class EditorOpeningException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public EditorOpeningException(final String message) {
            super(message);
        }

        public EditorOpeningException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    private static class LineSelectionException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public LineSelectionException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}