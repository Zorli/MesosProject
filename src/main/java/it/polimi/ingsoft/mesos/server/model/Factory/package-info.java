/**
 * Contains the factory and parsing components that bootstrap playable game instances.
 *
 * <p>The classes in this package translate static configuration data into runtime model objects.
 * They lazily parse the deck definition file, keep immutable blueprints for cards, and assemble
 * decks, boards, and game state objects for each newly created match.
 */
package it.polimi.ingsoft.mesos.server.model.Factory;
