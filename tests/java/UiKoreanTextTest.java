import com.namanseul.farmingmod.network.UiKoreanText;
public final class UiKoreanTextTest {
 public static void main(String[] args) {
  if(!UiKoreanText.value("gold").equals("돈"))throw new AssertionError();
  if(!UiKoreanText.value("price_bonus").equals("가격 보정"))throw new AssertionError();
  if(!UiKoreanText.value("minecraft:diamond").equals("minecraft:diamond"))throw new AssertionError();
  if(!UiKoreanText.value("Diamond").equals("Diamond"))throw new AssertionError();
  if(!UiKoreanText.error("[PENDING] Unknown transport problem").startsWith("[PENDING] "))throw new AssertionError();
  if(!UiKoreanText.error("insufficient balance").equals("잔액이 부족합니다."))throw new AssertionError();
  if(!UiKoreanText.error("이미 수령했습니다.").equals("이미 수령했습니다."))throw new AssertionError();
  System.out.println("UiKoreanTextTest passed");
 }
}
