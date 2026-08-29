package com.bfrisco.itemowners.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatMessageGeneratorTest {
    @Test
    void middlePageLinksToPreviousAndNextPages() {
        Component pagination = ChatMessageGenerator.generatePagination("/itemhistory ABCD-EFGH-IJKL", 2, 3);

        assertEquals(
                List.of("/itemhistory ABCD-EFGH-IJKL 1", "/itemhistory ABCD-EFGH-IJKL 3"),
                runCommands(pagination)
        );
    }

    @Test
    void endPagesOnlyLinkInAvailableDirection() {
        Component firstPage = ChatMessageGenerator.generatePagination("/itemsowned Player", 1, 3);
        Component lastPage = ChatMessageGenerator.generatePagination("/itemsowned Player", 3, 3);

        assertEquals(List.of("/itemsowned Player 2"), runCommands(firstPage));
        assertEquals(List.of("/itemsowned Player 2"), runCommands(lastPage));
    }

    @Test
    void singlePageDoesNotAddNavigation() {
        Component pagination = ChatMessageGenerator.generatePagination("/itemsowned Player", 1, 1);

        assertEquals(List.of(), runCommands(pagination));
    }

    private static List<String> runCommands(Component component) {
        List<String> commands = new ArrayList<>();
        collectRunCommands(component, commands);
        return commands;
    }

    private static void collectRunCommands(Component component, List<String> commands) {
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null && clickEvent.action() == ClickEvent.Action.RUN_COMMAND) {
            commands.add(clickEvent.value());
        }

        for (Component child : component.children()) {
            collectRunCommands(child, commands);
        }
    }
}
