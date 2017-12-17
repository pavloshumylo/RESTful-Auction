package auction.websocket.handler;

import auction.domain.Bid;
import auction.domain.Lot;
import auction.dto.LotDTO;
import auction.service.BidService;
import auction.utils.AuctionException;
import auction.utils.LotException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static auction.utils.LotException.ErrorCode.ANOTHER_CURRENT_PRICE;

@Component
public class BidHandler extends TextWebSocketHandler {

    @Autowired
    private BidService bidService;
    @Autowired
    private ObjectMapper mapper;

    private Map<WebSocketSession, Integer> sessions = new ConcurrentHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(BidHandler.class);

    // TODO fix multiply make bid
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Bid bid = mapper.readValue(message.getPayload(), Bid.class);
        if (!sessions.containsKey(session))
            sessions.put(session, bid.getLot().getAuction().getId());
        try {
            Lot lot = bidService.makeBid(bid);
            for (Map.Entry<WebSocketSession, Integer> entry : sessions.entrySet()) {
                if (lot.getAuction().getId() == entry.getValue()) {
                    entry.getKey().sendMessage(new TextMessage(mapper.writeValueAsString(LotDTO.fromModel(lot))));
                }
            }
        } catch (LotException e) {
            log.warn("LotException " + e.getErrorCode() + " caused in handleTextMessage method");
            if (e.getErrorCode() == ANOTHER_CURRENT_PRICE) {
                session.sendMessage(new BinaryMessage("Lot price has been changed!".getBytes()));
                session.sendMessage(new TextMessage(mapper.writeValueAsString(LotDTO.fromModel(e.getLot()))));
            }
        } catch (AuctionException e) {
            log.info("hanleTextMessage method executed");
            session.sendMessage(new BinaryMessage("Auction has been closed!".getBytes()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("Removing session " + session.getId());
        sessions.remove(session);
        log.info("afterConnectionClosed method executed");
    }
}
