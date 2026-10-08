import com.google.gson.JsonObject;
import com.namanseul.farmingmod.server.shop.ListingRequestReceipts;
import java.util.UUID;

public class ListingRequestReceiptsTest {
    static final class State {
        int inventory = 10, escrow;
        ListingRequestReceipts.Receipt receipt;
        JsonObject register() { inventory -= 3; escrow += 3; JsonObject value=new JsonObject(); value.addProperty("quantity",escrow); return value; }
        JsonObject cancel() { inventory += escrow; escrow=0; JsonObject value=new JsonObject();value.addProperty("returned",true);return value; }
    }
    static void check(boolean value) { if(!value)throw new AssertionError(); }
    static void denied(Runnable task) { try {task.run();} catch(RuntimeException expected){return;}throw new AssertionError(); }
    static ListingRequestReceipts.Request request(String id,String action,int quantity,int slot) {
        return new ListingRequestReceipts.Request(id,action,"mod:fish",quantity,slot);
    }
    public static void main(String[] args) {
        String id=UUID.randomUUID().toString(); State state=new State();
        var register=request(id,"register",3,2);
        JsonObject first=ListingRequestReceipts.perform(register,null,state::register,r->state.receipt=r);
        check(!first.get("replayed").getAsBoolean()); check(state.inventory==7 && state.escrow==3);
        for(int i=0;i<8;i++)check(ListingRequestReceipts.perform(register,state.receipt,state::register,r->{throw new AssertionError();}).get("replayed").getAsBoolean());
        check(state.inventory==7 && state.escrow==3);
        denied(()->ListingRequestReceipts.perform(request(id,"register",4,2),state.receipt,state::register,r->{throw new AssertionError();}));
        denied(()->ListingRequestReceipts.perform(request(id,"register",3,1),state.receipt,state::register,r->{throw new AssertionError();}));
        denied(()->ListingRequestReceipts.perform(request(id,"cancel",0,-1),state.receipt,state::cancel,r->{throw new AssertionError();}));
        var other=new ListingRequestReceipts.Request(id,"register","mod:other",3,2);
        denied(()->ListingRequestReceipts.perform(other,state.receipt,state::register,r->{throw new AssertionError();}));
        check(state.inventory==7 && state.escrow==3);
        // Restoring the receipt after restart replays the stored snapshot, not the live listing.
        var saved=new ListingRequestReceipts.Receipt(register,state.receipt.result().deepCopy());
        state.escrow=99;
        JsonObject replay=ListingRequestReceipts.perform(register,saved,state::register,r->{throw new AssertionError();});
        check(replay.get("quantity").getAsInt()==3 && state.escrow==99);
        replay.addProperty("quantity",900); check(saved.result().get("quantity").getAsInt()==3);
        // An uncertain save retains the in-memory receipt; retry does not reapply the mutation.
        State failed=new State();
        denied(()->ListingRequestReceipts.perform(register,null,failed::register,r->{failed.receipt=r;throw new IllegalStateException("save unavailable");}));
        check(failed.inventory==7 && failed.escrow==3);
        ListingRequestReceipts.perform(register,failed.receipt,failed::register,r->{throw new AssertionError();});
        check(failed.inventory==7 && failed.escrow==3);
        var cancel=request(UUID.randomUUID().toString(),"cancel",0,-1);
        ListingRequestReceipts.perform(cancel,null,failed::cancel,r->failed.receipt=r);
        ListingRequestReceipts.perform(cancel,failed.receipt,failed::cancel,r->{throw new AssertionError();});
        check(failed.inventory==10 && failed.escrow==0);
        denied(()->request("bad","register",3,2)); denied(()->request(id,"register",0,2));
        denied(()->request(id,"cancel",1,-1)); denied(()->request(id,"cancel",0,2));
        System.out.println("Listing request replay passed");
    }
}
