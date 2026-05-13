package it.polimi.ingsoft.mesos.client.view.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TuiCommandParserTest {
    private final TuiCommandParser parser = new TuiCommandParser();

    @Test
    void parsesSupportedCommands() {
        assertCommand("help", TuiCommandType.HELP, null);
        assertCommand("auth ada", TuiCommandType.AUTH, "ada");
        assertCommand("list-lobbies", TuiCommandType.LIST_LOBBIES, null);
        assertCommand("create-lobby 2", TuiCommandType.CREATE_LOBBY, "2");
        assertCommand("join-lobby 7", TuiCommandType.JOIN_LOBBY, "7");
        assertCommand("quit-lobby", TuiCommandType.QUIT_LOBBY, null);
        assertCommand("start-lobby", TuiCommandType.START_LOBBY, null);
        assertCommand("move-totem A1", TuiCommandType.MOVE_TOTEM, "A1");
        assertCommand("choose-card 11", TuiCommandType.CHOOSE_CARD, "11");
        assertCommand("extra-draw 12", TuiCommandType.EXTRA_DRAW, "12");
        assertCommand("quit", TuiCommandType.QUIT, null);
    }

    @Test
    void parsesAliases() {
        assertCommand("lobbies", TuiCommandType.LIST_LOBBIES, null);
        assertCommand("create 3", TuiCommandType.CREATE_LOBBY, "3");
        assertCommand("join 5", TuiCommandType.JOIN_LOBBY, "5");
        assertCommand("exit", TuiCommandType.QUIT, null);
        assertCommand("h", TuiCommandType.HELP, null);
        assertCommand("ll", TuiCommandType.LIST_LOBBIES, null);
        assertCommand("cl 4",TuiCommandType.CREATE_LOBBY, "4");
        assertCommand("jl 3",TuiCommandType.JOIN_LOBBY, "3");
        assertCommand("ql",TuiCommandType.QUIT_LOBBY, null);
        assertCommand("sl", TuiCommandType.START_LOBBY, null);
        assertCommand("mt A1", TuiCommandType.MOVE_TOTEM, "A1");
        assertCommand("cc 11", TuiCommandType.CHOOSE_CARD, "11");
        assertCommand("ed 12", TuiCommandType.EXTRA_DRAW, "12");
        assertCommand("q", TuiCommandType.QUIT, null);
    }

    @Test
    void rejectsMissingRequiredArgument() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> this.parser.parse("auth")
        );

        assertEquals("Usage: auth <nickname>", exception.getMessage());
    }

    @Test
    void rejectsUnexpectedArgument() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> this.parser.parse("help now")
        );

        assertEquals("Usage: help", exception.getMessage());
    }

    @Test
    void rejectsTooManyArguments() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> this.parser.parse("create-lobby 2 extra")
        );

        assertEquals("Usage: create-lobby <capacity>", exception.getMessage());
    }

    @Test
    void rejectsInvalidIntegerArgument() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> this.parser.parse("join-lobby abc")
        );

        assertEquals("Usage: join-lobby <lobby-id>", exception.getMessage());
    }

    @Test
    void rejectsUnknownCommand() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> this.parser.parse("status")
        );

        assertEquals("Unknown command. Type 'help'.", exception.getMessage());
    }

    private void assertCommand(String line, TuiCommandType expectedType, String expectedArgument) {
        TuiCommand command = this.parser.parse(line);

        assertEquals(expectedType, command.type());
        assertEquals(expectedArgument, command.argument());
    }
}
