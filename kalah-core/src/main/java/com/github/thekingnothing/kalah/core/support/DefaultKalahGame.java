package com.github.thekingnothing.kalah.core.support;

import com.github.thekingnothing.kalah.core.GameAlreadyStartedException;
import com.github.thekingnothing.kalah.core.GameStatus;
import com.github.thekingnothing.kalah.core.IllegalPlayerException;
import com.github.thekingnothing.kalah.core.IllegalTurnException;
import com.github.thekingnothing.kalah.core.KalahGame;
import com.github.thekingnothing.kalah.core.KalahGameDesk;
import com.github.thekingnothing.kalah.core.PlayerHouse;
import com.github.thekingnothing.kalah.core.PlayerStore;
import com.github.thekingnothing.kalah.core.Player;

import java.util.List;

public class DefaultKalahGame implements KalahGame {
    
    static final int HOUSES_PER_PLAYER = 6;
    private static final int MAX_HOUSES_INDEX = HOUSES_PER_PLAYER - 1;
    
    private final int stonesCount;
    
    private Players players;
    private GameStatus status;
    private KalahGameDesk gameDesk;
    private Player nextPlayer;
    private Player winner;
    private int halfOfStones;
    private boolean captureResult;
    
    public DefaultKalahGame(final int stonesCount) {
        this.stonesCount = stonesCount;
    }
    
    @Override
    public void start(final Player playerOne, final Player playerTwo) {
        if (status == GameStatus.Started) {
            throw new GameAlreadyStartedException("Game is already started.");
        }
        halfOfStones = HOUSES_PER_PLAYER * stonesCount;
        
        players = new Players(playerOne, playerTwo);
        nextPlayer = playerOne;
        gameDesk = new ArrayKalahGameDesk(players, stonesCount);
        status = GameStatus.Started;
    }
    
    @Override
    public KalahGameDesk getGameDesk() {
        return gameDesk;
    }
    
    @Override
    public GameStatus getStatus() {
        return status;
    }
    
    @Override
    public Player getWinner() {
        return winner;
    }
    
    @Override
    public void makeTurn(final PlayerHouse startHouse) {
        validateTurn(startHouse);
        
        final Player turnPlayer = startHouse.getPlayer();
        final Player opponent = players.getOpponent(turnPlayer);
        
        pickUpAllStones(startHouse, turnPlayer);
        moveStones(startHouse, turnPlayer, opponent);
        tryToCaptureStones(turnPlayer, opponent);
        determinateNextPlayer(turnPlayer, opponent);
        finishTheGameIfRequired(turnPlayer, opponent);
    }
    
    private void finishTheGameIfRequired(final Player turnPlayer, final Player opponent) {
        if (nextPlayerDoesNotHaveStones()) {
            this.status = GameStatus.Finished;
            gameDesk = gameDesk.pickUpAllStones()
                               .putAllStoneToPlayerStore(turnPlayer);
            checkWinningConditions(turnPlayer, opponent);
        } else if (checkWinningConditions(turnPlayer)) {
            this.status = GameStatus.Finished;
            this.winner = turnPlayer;
        }
    }
    
    private boolean nextPlayerDoesNotHaveStones() {
        final List<PlayerHouse> playerHouses = gameDesk.getPlayerHouses(nextPlayer);
        
        boolean turnPlayerWin = true;
        int index = 0;
        while (turnPlayerWin && index < playerHouses.size()) {
            if (playerHouses.get(index).getStones() > 0) {
                turnPlayerWin = false;
            }
            index++;
        }
        return turnPlayerWin;
    }
    
    private void checkWinningConditions(final Player turnPlayer, final Player opponent) {
        if (checkWinningConditions(turnPlayer)) {
            winner = turnPlayer;
        } else if (checkWinningConditions(opponent)) {
            winner = opponent;
        }
    }
    
    private boolean checkWinningConditions(final Player turnPlayer) {
        return new WinCondition(gameDesk, turnPlayer, halfOfStones).check();
    }
    
    private void pickUpAllStones(final PlayerHouse startHouse, final Player turnPlayer) {
        gameDesk = gameDesk.pickUpAllStones(turnPlayer, startHouse.getHouseIndex());
        assertThatHouseHasStones();
    }
    
    private void validateTurn(final PlayerHouse startHouse) {
        new TurnValidator(startHouse).validate();
    }
    
    
    private void tryToCaptureStones(final Player turnPlayer, final Player opponent) {
        captureResult = false;
        if (gameDesk.getLastLocation() instanceof PlayerHouse) {
            final PlayerHouse lastLocation = (PlayerHouse) gameDesk.getLastLocation();
            final int houseIndex = lastLocation.getHouseIndex();
            final int opponentHouseIndex = Math.abs(MAX_HOUSES_INDEX - houseIndex);
            final PlayerHouse opponentHouse = gameDesk.getPlayerHouses(opponent).get(opponentHouseIndex);
            
            if (turnPlayer.equals(lastLocation.getPlayer()) && opponentHouse.getStones() > 0) {
                captureStones(turnPlayer, opponent, houseIndex, opponentHouseIndex);
                captureResult = true;
            }
        }
    }
    
    private void captureStones(final Player turnPlayer, final Player opponent, final int houseIndex, final int opponentHouseIndex) {
        gameDesk = gameDesk.pickUpAllStones(turnPlayer, houseIndex)
                           .pickUpAllStones(opponent, opponentHouseIndex)
                           .putAllStoneToPlayerStore(turnPlayer);
    }
    
    void setGameDesk(final KalahGameDesk gameDesk) {
        this.gameDesk = gameDesk;
    }
    
    private void determinateNextPlayer(final Player turnPlayer, final Player opponent) {
        if (captureResult) {
            nextPlayer = opponent;
        } else {
            if (gameDesk.getLastLocation() instanceof PlayerStore) {
                nextPlayer = turnPlayer;
            } else {
                nextPlayer = opponent;
            }
        }
    }
    
    private void moveStones(final PlayerHouse startHouse, final Player player, final Player opponent) {
        int startIndex = startHouse.getHouseIndex() + 1;
        do {
            gameDesk = gameDesk.putStonesToPlayerHouses(player, startIndex)
                               .putStoneToPlayerStore(player)
                               .putStonesToPlayerHouses(opponent, 0);
            startIndex = 0;
        }
        while (gameDesk.hasStonesOutOfDesk());
    }
    
    private void assertThatHouseHasStones() {
        if (!gameDesk.hasStonesOutOfDesk()) {
            throw new IllegalTurnException("You cannot start your turn from empty house.");
        }
    }
    
    private class TurnValidator {
        private final PlayerHouse startHouse;
        
        public TurnValidator(final PlayerHouse startHouse) {this.startHouse = startHouse;}
        
        public void validate() {
            assertHouseIndexInARange(startHouse);
            assertPlayerInAGame(startHouse.getPlayer());
            assertThatPlayerMakeTurnInOrder(startHouse.getPlayer());
        }
        
        private void assertHouseIndexInARange(final PlayerHouse startHouse) {
            if (startHouse.getHouseIndex() < 0 || MAX_HOUSES_INDEX < startHouse.getHouseIndex()) {
                throw new IllegalTurnException("House index is out of range.");
            }
        }
        
        private void assertPlayerInAGame(final Player player) {
            if (!players.contains(player)) {
                throw new IllegalPlayerException("Player " + player + " cannot make turn, because he did not part of the game.");
            }
        }
        
        private void assertThatPlayerMakeTurnInOrder(final Player turnPlayer) {
            if (!nextPlayer.equals(turnPlayer)) {
                throw new IllegalTurnException("Player " + turnPlayer.getId() + " is tying to make turn out of order.");
            }
        }
    }
}