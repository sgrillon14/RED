package org.robotframework.ide.eclipse.main.plugin.tableeditor.source.colouring;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.robotframework.red.junit.Conditions.absent;
import static org.robotframework.red.junit.Conditions.present;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Random;

import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.rules.Token;
import org.junit.Test;
import org.rf.ide.core.testdata.text.read.IRobotLineElement;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;
import org.rf.ide.core.testdata.text.read.separators.Separator;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.source.colouring.ISyntaxColouringRule.PositionedTextToken;

public class KeywordNameRuleTest {

    private final KeywordNameRule testedRule = new KeywordNameRule(new Token("name_token"), new Token("var_token"));

    @Test
    public void ruleIsApplicableOnlyForRobotTokens() {
        assertThat(testedRule.isApplicable(new RobotToken())).isTrue();
        assertThat(testedRule.isApplicable(new Separator())).isFalse();
        assertThat(testedRule.isApplicable(mock(IRobotLineElement.class))).isFalse();
    }

    @Test
    public void simpleKeywordNameIsRecognized() {
        boolean thereWasName = false;
        for (final RobotToken token : TokensSource.createTokens()) {
            final Optional<PositionedTextToken> evaluatedToken = evaluate(token);

            if (token.getText().contains("userkw")) {
                thereWasName = true;

                assertThat(evaluatedToken).is(present());
                assertThat(evaluatedToken.get().getPosition())
                        .isEqualTo(new Position(token.getStartOffset(), token.getText().length()));
                assertThat(evaluatedToken.get().getToken().getData()).isEqualTo("name_token");

            } else {
                assertThat(evaluatedToken).is(absent());
            }
        }
        assertThat(thereWasName).isTrue();
    }

    @Test
    public void simpleKeywordNameIsRecognized_evenWhenPositionIsInsideToken() {
        boolean thereWasName = false;
        for (final RobotToken token : TokensSource.createTokens()) {
            final int positionInsideToken = new Random().nextInt(token.getText().length());
            final Optional<PositionedTextToken> evaluatedToken = evaluate(token, positionInsideToken);

            if (token.getText().contains("userkw")) {
                thereWasName = true;

                assertThat(evaluatedToken).is(present());
                assertThat(evaluatedToken.get().getPosition())
                        .isEqualTo(new Position(token.getStartOffset(), token.getText().length()));
                assertThat(evaluatedToken.get().getToken().getData()).isEqualTo("name_token");

            } else {
                assertThat(evaluatedToken).is(absent());
            }
        }
        assertThat(thereWasName).isTrue();
    }

    @Test
    public void variableTokenIsDetected_whenPositionedInsideVariable() {
        final String var1 = "${var}";
        final String var2 = "@{list}";
        final String var3 = "&{dir}";

        final String content = "abc" + var1 + "def" + var2 + "ghi" + var3 + "[0]jkl";

        final Collection<Position> varPositions = newArrayList();
        varPositions.add(new Position(content.indexOf(var1), var1.length()));
        varPositions.add(new Position(content.indexOf(var2), var2.length()));
        varPositions.add(new Position(content.indexOf(var3), var3.length()));

        final RobotToken token = createToken(content);

        for (final Position position : varPositions) {
            for (int i = position.getOffset(); i < position.getLength(); i++) {
                final Optional<PositionedTextToken> evaluatedToken = testedRule.evaluate(token, i,
                        new ArrayList<IRobotLineElement>());
                assertThat(evaluatedToken).is(present());
                assertThat(evaluatedToken.get().getPosition()).isEqualTo(position);
                assertThat(evaluatedToken.get().getToken().getData()).isEqualTo("var_token");
            }
        }
    }

    @Test
    public void keywordNameTokenIsDetected_whenPositionedOutsideVariables() {
        final String def1 = "abc";
        final String def2 = "def";
        final String def3 = "ghi";
        final String def4 = "[0]jkl";
        final String content = def1 + "${var}" + def2 + "@{list}" + def3 + "&{dir}" + def4;

        final Collection<Position> varPositions = newArrayList();
        varPositions.add(new Position(content.indexOf(def1), def1.length()));
        varPositions.add(new Position(content.indexOf(def2), def2.length()));
        varPositions.add(new Position(content.indexOf(def3), def3.length()));
        varPositions.add(new Position(content.indexOf(def4), def4.length()));

        final RobotToken token = createToken(content);

        for (final Position position : varPositions) {
            for (int i = position.getOffset(); i < position.getLength(); i++) {
                final Optional<PositionedTextToken> evaluatedToken = testedRule.evaluate(token, i,
                        new ArrayList<IRobotLineElement>());
                assertThat(evaluatedToken).is(present());
                assertThat(evaluatedToken.get().getPosition()).isEqualTo(position);
                assertThat(evaluatedToken.get().getToken().getData()).isEqualTo("name_token");
            }
        }
    }

    private RobotToken createToken(final String kwName) {
        final RobotToken token = RobotToken.create(kwName,
                newArrayList(RobotTokenType.KEYWORD_NAME, RobotTokenType.VARIABLE_USAGE));
        token.setLineNumber(1);
        token.setStartColumn(0);
        token.setStartOffset(0);
        return token;
    }

    private Optional<PositionedTextToken> evaluate(final RobotToken token) {
        return evaluate(token, 0);
    }

    private Optional<PositionedTextToken> evaluate(final RobotToken token, final int position) {
        return testedRule.evaluate(token, position, new ArrayList<IRobotLineElement>());
    }
}
