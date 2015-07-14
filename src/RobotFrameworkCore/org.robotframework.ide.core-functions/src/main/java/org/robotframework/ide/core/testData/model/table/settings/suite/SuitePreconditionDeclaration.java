package org.robotframework.ide.core.testData.model.table.settings.suite;

import org.robotframework.ide.core.testData.model.AModelElement;
import org.robotframework.ide.core.testData.model.LineElement;
import org.robotframework.ide.core.testData.model.LineElement.ElementType;
import org.robotframework.ide.core.testData.model.RobotLine;


public class SuitePreconditionDeclaration extends AModelElement {

    public SuitePreconditionDeclaration(RobotLine containingLine,
            LineElement originalElement) {
        super(ElementType.SETTINGS_SUITE_PRECONDITION, containingLine,
                originalElement);
    }
}
