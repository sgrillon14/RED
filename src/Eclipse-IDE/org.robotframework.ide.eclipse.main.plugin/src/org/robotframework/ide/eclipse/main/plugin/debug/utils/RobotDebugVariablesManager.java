/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.debug.utils;

import static com.google.common.collect.Lists.newArrayList;

import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.robotframework.ide.eclipse.main.plugin.debug.model.RobotDebugTarget;
import org.robotframework.ide.eclipse.main.plugin.debug.model.RobotDebugValue;
import org.robotframework.ide.eclipse.main.plugin.debug.model.RobotDebugValueOfDictionary;
import org.robotframework.ide.eclipse.main.plugin.debug.model.RobotDebugVariable;

/**
 * @author mmarzec
 */
@SuppressWarnings("PMD.GodClass")
public class RobotDebugVariablesManager {

    private static final String GLOBAL_VARIABLE_NAME = "Global Variables";

    private static final String SUITE_VARIABLE_PREFIX = "SUITE_";

    private static final String TEST_VARIABLE_PREFIX = "TEST_";

    private static final int VARIABLE_NAME_OFFSET = 2;

    private final RobotDebugTarget target;

    private final ConcurrentLinkedDeque<RobotDebugVariablesContext> previousVariables;

    private Map<String, String> globalVariables;

    private final Map<String, RobotDebugVariable> nestedGlobalVars;

    private final LinkedList<String> sortedVariablesNames;

    public RobotDebugVariablesManager(final RobotDebugTarget target) {
        this.target = target;
        this.previousVariables = new ConcurrentLinkedDeque<>();
        this.globalVariables = new LinkedHashMap<>();
        this.nestedGlobalVars = new LinkedHashMap<>();
        this.sortedVariablesNames = new LinkedList<>();
    }

    /**
     * Extract and sort variables for given StackTrace level.
     * Every level of StackTrace has its own context in previousVariables map. Current variables are
     * compared with previous state.
     * If current level of StackTrace has not any previous state, then previous state will be
     * variables from level below. This is
     * for saving previous order of variables in higher levels.
     *
     * @param stackTraceId
     * @param newVariables
     * @return
     */
    public IVariable[] extractRobotDebugVariables(final int stackTraceId, final Map<String, Object> newVariables) {

        final RobotDebugVariablesContext currentVariablesContext = findCurrentVariablesContext(stackTraceId);
        final Map<String, IVariable> previousVariablesMap = initPreviousVariablesState(currentVariablesContext);

        final Map<String, IVariable> nonGlobalVariablesMap = new LinkedHashMap<>();
        if (previousVariablesMap == null) {
            initNewNonGlobalVariables(newVariables, nonGlobalVariablesMap);
        } else {
            initVariablesComparingWithPreviousState(newVariables, previousVariablesMap, nonGlobalVariablesMap);
        }

        final LinkedList<IVariable> currentVariablesList = createCurrentVariablesList(nonGlobalVariablesMap);

        saveCurrentVariablesState(stackTraceId, currentVariablesContext, nonGlobalVariablesMap);

        return currentVariablesList.toArray(new IVariable[currentVariablesList.size()]);
    }

    private RobotDebugVariablesContext findCurrentVariablesContext(final int stackTraceId) {
        for (final RobotDebugVariablesContext variablesContext : previousVariables) {
            if (variablesContext.getStackTraceId() == stackTraceId) {
                return variablesContext;
            }
        }
        return null;
    }

    private Map<String, IVariable> initPreviousVariablesState(
            final RobotDebugVariablesContext currentVariablesContext) {
        Map<String, IVariable> previousVariablesMap = null;
        if (currentVariablesContext != null) {
            previousVariablesMap = currentVariablesContext.getVariablesMap();
        } else if (!previousVariables.isEmpty()) {
            previousVariablesMap = previousVariables.getLast().getVariablesMap();
        }
        return previousVariablesMap;
    }

    private void initNewNonGlobalVariables(final Map<String, Object> newVariables,
            final Map<String, IVariable> nonGlobalVariablesMap) {
        for (final String variableName : sortedVariablesNames) {
            if (!globalVariables.containsKey(variableName) && newVariables.containsKey(variableName)) {
                nonGlobalVariablesMap.put(variableName,
                        new RobotDebugVariable(target, variableName, newVariables.get(variableName)));
            }
        }
    }

    private void initVariablesComparingWithPreviousState(final Map<String, Object> newVariables,
            final Map<String, IVariable> previousVariablesMap, final Map<String, IVariable> nonGlobalVariablesMap) {
        for (final String variableName : sortedVariablesNames) {
            if (newVariables.containsKey(variableName)) {
                if (!globalVariables.containsKey(variableName)) {
                    final RobotDebugVariable newVariable = new RobotDebugVariable(target, variableName,
                            newVariables.get(variableName));
                    newVariable.setHasValueChanged(hasValueChanged(variableName, newVariable, previousVariablesMap));
                    nonGlobalVariablesMap.put(variableName, newVariable);
                } else {
                    if (isNewGlobalVariable(newVariables, variableName)) {
                        nestedGlobalVars.put(variableName,
                                new RobotDebugVariable(target, variableName, newVariables.get(variableName)));
                    }
                }
            }
        }
    }

    private boolean hasValueChanged(final String newVarName, final RobotDebugVariable newVariable,
            final Map<String, IVariable> previousVariablesMap) {

        if (previousVariablesMap.containsKey(newVarName)) {
            final RobotDebugVariable previousVariable = (RobotDebugVariable) previousVariablesMap.get(newVarName);
            if (newVariable.getValue().hasVariables() && previousVariable.getValue().hasVariables()) {
                return compareNestedVariables(newVariable.getValue().getVariables(),
                        previousVariable.getValue().getVariables());
            }
            final String variablePreviousValue = previousVariable.getValue().getValueString();
            final String variableNewValue = newVariable.getValue().getValueString();
            if (variablePreviousValue != null && !variablePreviousValue.equals(variableNewValue)
                    && previousVariable.supportsValueModification()) {
                return true;
            }
        }
        return false;
    }

    private boolean compareNestedVariables(final IVariable[] newNestedVars, final IVariable[] prevNestedVars) {

        if (newNestedVars.length != prevNestedVars.length) {
            return true;
        }
        boolean hasNestedValueChanged = false;
        try {
            for (int i = 0; i < newNestedVars.length; i++) {
                final IValue newValue = newNestedVars[i].getValue();
                final IValue previousValue = prevNestedVars[i].getValue();
                if (newValue.hasVariables() && previousValue.hasVariables()) {
                    compareNestedVariables(newValue.getVariables(), previousValue.getVariables());
                } else if (previousValue.getValueString() != null
                        && !previousValue.getValueString().equals(newValue.getValueString())) {
                    ((RobotDebugVariable) newNestedVars[i]).setHasValueChanged(true);
                    hasNestedValueChanged = true;
                }
            }
        } catch (final DebugException e) {
            e.printStackTrace();
        }
        return hasNestedValueChanged;
    }

    private boolean isNewGlobalVariable(final Map<String, Object> newVariables, final String variableName) {
        return !newVariables.get(variableName).equals(globalVariables.get(variableName));
    }

    private LinkedList<IVariable> createCurrentVariablesList(final Map<String, IVariable> nonGlobalVariablesMap) {
        final LinkedList<IVariable> currentVariablesList = new LinkedList<>();
        currentVariablesList.addAll(nonGlobalVariablesMap.values());
        currentVariablesList.addLast(createGlobalVariable());
        return currentVariablesList;
    }

    private void saveCurrentVariablesState(final int stackTraceId,
            final RobotDebugVariablesContext currentVariablesContext,
            final Map<String, IVariable> nonGlobalVariablesMap) {
        if (currentVariablesContext != null) {
            currentVariablesContext.setVariablesMap(nonGlobalVariablesMap);
        } else {
            previousVariables.add(new RobotDebugVariablesContext(stackTraceId, nonGlobalVariablesMap));
        }
    }

    public Deque<RobotDebugVariablesContext> getPreviousVariables() {
        return previousVariables;
    }

    public void sortVariablesNames(final Map<String, Object> vars) {
        final String[] varArray = vars.keySet().toArray(new String[vars.keySet().size()]);
        for (int i = varArray.length - 1; i >= 0; i--) {
            final String varName = varArray[i];
            if (!sortedVariablesNames.contains(varName)) {
                if (varName.startsWith(SUITE_VARIABLE_PREFIX, VARIABLE_NAME_OFFSET)) {
                    sortedVariablesNames.addLast(varName);
                } else if (varName.startsWith(TEST_VARIABLE_PREFIX, VARIABLE_NAME_OFFSET)) {
                    sortedVariablesNames.addLast(varName);
                } else {
                    sortedVariablesNames.addFirst(varName);
                }
            }
        }
    }

    public Map<String, String> getGlobalVariables() {
        return globalVariables;
    }

    public void setGlobalVariables(final Map<String, String> globalVariables) {
        if (globalVariables != null) {
            this.globalVariables = globalVariables;
            this.globalVariables.entrySet().forEach(entry -> {
                final RobotDebugVariable variable = new RobotDebugVariable(target, entry.getKey(), entry.getValue());
                variable.disableValueModificationSupport();
                nestedGlobalVars.put(entry.getKey(), variable);
            });
        }
    }

    private RobotDebugVariable createGlobalVariable() {
        final RobotDebugValue value = new RobotDebugValueOfDictionary(target, "",
                newArrayList(nestedGlobalVars.values()));
        final RobotDebugVariable variable = new RobotDebugVariable(target, GLOBAL_VARIABLE_NAME, value);
        variable.disableValueModificationSupport();
        return variable;
    }
}
