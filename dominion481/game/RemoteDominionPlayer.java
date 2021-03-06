package dominion481.game;

import static dominion481.game.DominionAction.cardSelectionActions;
import static dominion481.game.DominionAction.cardsSelectionActions;
import static dominion481.game.DominionAction.defaultActions;
import static dominion481.game.DominionAction.passableCardSelectionActions;
import static dominion481.game.DominionAction.passableCardsSelectionActions;
import static dominion481.game.DominionAction.treasurePhaseActions;
import static dominion481.game.DominionAction.yesNoActions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dominion481.game.Card.Type;
import dominion481.server.Action;
import dominion481.server.ClientHandler;
import dominion481.server.RemotePlayer;

public class RemoteDominionPlayer extends DominionPlayer implements
      RemotePlayer {
   private ClientHandler client;
   private List<Action> acts = defaultActions;
   Object ret;
   
   private void waitOnClient() {
      synchronized (client) {
         try {
            client.wait();
         } catch (InterruptedException e) {
            throw new RuntimeException(e);
         }
      }
   }
   
   private void getResponse(List<Action> actions, String prompt) {
      List<Action> prev = this.acts;
      this.acts = actions;
      
      notify(prompt);
      waitOnClient();
      
      this.acts = prev;
   }
   
   private void getResponse(List<Action> actions, String prompt, List<?> objs) {
      List<Action> prev = this.acts;
      this.acts = actions;
      
      client.write(prompt, objs);
      waitOnClient();
      
      this.acts = prev;
   }
   
   public void setRet(Object o) {
      ret = o;
      synchronized (client) { client.notify(); }
   }
   
   private Object getRet() {
      Object out = ret;
      ret = null;
      return out;
   }

   @SuppressWarnings("unchecked")
   @Override
   public List<Card> cellar() {
      getResponse(cardsSelectionActions, "cellarDiscard", hand);
      return (List<Card>)getRet();
   }

   @SuppressWarnings("unchecked")
   @Override
   public List<Card> chapel() {
      getResponse(cardsSelectionActions, "chapelDiscard", hand);
      return (List<Card>)getRet();
   }

   @Override
   public boolean chancellor() {
      getResponse(yesNoActions, "chancellorDeckDiscard?");
      return (Boolean)getRet();
   }

   @Override
   public Card workshop() {
      List<Card> toBuy = new ArrayList<Card>(parentGame.boardMap.keySet());

      for (int i = 0; i < toBuy.size(); i++)
         if (toBuy.get(i).getCost() > 4)
            toBuy.remove(i--);

      getResponse(passableCardSelectionActions, "workshopGain", toBuy);
      return (Card)getRet();
   }

   @Override
   public Card feast() {
      List<Card> toBuy = new ArrayList<Card>(parentGame.boardMap.keySet());

      for (int i = 0; i < toBuy.size(); i++)
         if (toBuy.get(i).getCost() > 5)
            toBuy.remove(i--);

      getResponse(passableCardSelectionActions, "feastGain", toBuy);
      return (Card)getRet();
   }

   @Override
   public Card[] remodel() {
      Card[] out = new Card[2];
      getResponse(cardSelectionActions, "remodelDiscard", hand);
      out[0] = (Card) getRet();
      getResponse(cardSelectionActions, "remodelGain", Card.filter(
            parentGame.getPurchaseableCards(), -1, out[0].getCost() + 2));
      out[1] = (Card) getRet();
      return out;
   }

   @Override
   public Card throneRoom() {
      List<Card> actionCards = Card.filter(hand, Type.ACTION);

      if (actionCards.size() == 0)
         return null;

      getResponse(passableCardSelectionActions, "throneRoomPlay", actionCards);

      return (Card)getRet();
   }

   @Override
   public boolean libraryDiscard(Card card) {
      getResponse(yesNoActions, "keep? "+card);

      return !(Boolean)getRet();
   }

   @Override
   public Card[] mine() {
      Card[] out = new Card[2];

      getResponse(cardSelectionActions, "mineDiscard", Card.filter(hand, Type.TREASURE));
      out[0] = (Card) getRet();
      getResponse(cardSelectionActions, "mineGain",
            Card.filter(Arrays.asList(Card.values()), Type.TREASURE));
      out[1] = (Card) getRet();

      return out;
   }

   @Override
   public Card bureaucrat() {
      getResponse(cardSelectionActions, "bureaucratPutback", Card.filter(hand, Type.VICTORY));
      return (Card) getRet();
   }

   @SuppressWarnings("unchecked")
   @Override
   public List<Card> militia() {
      getResponse(cardsSelectionActions, "militiaDiscard", hand);
      return (List<Card>) getRet();
   }

   @Override
   public boolean spyDiscard(Card c, DominionPlayer p) {
      getResponse(yesNoActions, "spyDiscard? "+p+" "+c);
      return (Boolean) getRet();
   }

   @Override
   public boolean theifGain(Card c) {
      getResponse(yesNoActions, "theifGain? "+c);
      return (Boolean)getRet();
   }

   @Override
   public void actionPhase() {
      notify("hand"+getCardColors(hand));
      while (actions > 0) {
         List<Card> actionCards = Card.filter(hand, Type.ACTION);
   
         if (actionCards.size() == 0)
            return;
   
         getResponse(passableCardSelectionActions, "actionPhase "+actions, actionCards);
         
         Card c = (Card)getRet();
         if (c == null)
            return;
         playAction(c);
      }
   }

   @SuppressWarnings("unchecked")
   @Override
   public void treasurePhase() {
      List<Card> treasureCards = Card.filter(hand, Type.TREASURE);
      
      getResponse(treasurePhaseActions, "treasurePhase" + getCardColors(treasureCards));
      
      List<Card> toRedeem = (List<Card>)getRet();
      for (Card c : toRedeem) {
         try {
            playTreasure(c);
         } catch (IllegalStateException e) {
            client.write("invalidCard " + c);
         }
      }
   }

   @SuppressWarnings("unchecked")
   @Override
   public void buyPhase() {
      List<Card> availableCards = new ArrayList<Card>();
      for (Card c : parentGame.boardMap.keySet())
         if (c.getCost() <= coin)
            availableCards.add(c);      

      Collections.sort(availableCards, new Comparator<Card>() {
         @Override
         public int compare(Card a, Card b) {
            return b.getCost() - a.getCost();
         }
      });

      while (buys > 0) {
         getResponse(passableCardsSelectionActions, "buyPhase "+coin+"C"+buys+"B"+getCardColors(availableCards));

         List<Card> toBuy = (List<Card>)getRet();
         if (toBuy == null)
            break;

         for (Card c : toBuy) {
            try {
               buy(c);
            } catch (IllegalArgumentException e) {
               client.write(e.getMessage());
            }
         }
         
         for (int i = 0; i < availableCards.size(); i++)
            if (availableCards.get(i).getCost() > coin)
               availableCards.remove(i--);
      }
   }

   @Override
   public ClientHandler getClient() {
      return client;
   }

   @Override
   public List<Action> getActions() {
      List<Action> out = new ArrayList<Action>(acts);
      out.add(DominionAction.statusAction);
      return out;
   }
   
   @Override
   public void notify(String s) {
      client.write(s);
   }

   public RemoteDominionPlayer(Dominion game, ClientHandler client) {
      super(game, client.getNick());
      this.client = client;
   }

   StringBuilder getCardColors(List<Card> cards) {
      StringBuilder sb = new StringBuilder();
      for (Card c : cards) {
         sb.append(' ');
         sb.append(c.getColorName());
      }
      return sb;
   }
   
   public String toString() {
      return client.getNick();
   }
}
