package org.robotframework.ide.core.testData.text.context.recognizer.escapeSequences;

import org.robotframework.ide.core.testData.text.context.ContextBuilder;
import org.robotframework.ide.core.testData.text.context.SimpleRobotContextType;
import org.robotframework.ide.core.testData.text.lexer.RobotSingleCharTokenType;


/**
 * Check if current line contains escaped dollar sign {@code '\$'}
 * 
 * @author wypych
 * @since JDK 1.7 update 74
 * @version Robot Framework 2.9 alpha 2
 * 
 * @see ContextBuilder
 * @see RobotSingleCharTokenType#SINGLE_ESCAPE_BACKSLASH
 * @see RobotSingleCharTokenType#SINGLE_SCALAR_BEGIN_DOLLAR
 * 
 * @see SimpleRobotContextType#ESCAPED_DOLLAR_SIGN
 */
public class EscapedDollarSign extends AEscapedRecognizer {

    public EscapedDollarSign() {
        super(SimpleRobotContextType.ESCAPED_DOLLAR_SIGN, '$', '$');
    }
}
