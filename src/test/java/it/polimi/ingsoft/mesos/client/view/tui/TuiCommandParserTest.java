package it.polimi.ingsoft.mesos.client.view.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TuiCommandParserTest {
    @Test
    void parseAuthenticateAliasReturnsExpectedCommand() {
        TuiCommand command = TuiCommandParser.parse("login Alice");

        assertEquals(TuiCommand.Type.AUTHENTICATE, command.getType());
        assertEquals("Alice", command.getTextArgument());
    }

    @Test
    void parseCreateLobbyRequiresIntegerArgument() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TuiCommandParser.parse("create-lobby nope")
        );

        assertEquals("Usage: create-lobby <integer>", exception.getMessage());
    }

    @Test
    void parseStatusRejectsUnexpectedArgument() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TuiCommandParser.parse("status now")
        );

        assertEquals("Usage: status", exception.getMessage());
    }

    @Test
    void parseBlankLineReturnsNoOpCommand() {
        TuiCommand command = TuiCommandParser.parse("   ");

        assertEquals(TuiCommand.Type.NO_OP, command.getType());
    }
}
