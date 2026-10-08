"""Pure Java protocol and insertion algorithm tests, not a NeoForge/game integration test."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / 'namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/server/shop'

class ShopJavaTests(unittest.TestCase):
    def test_protocol_and_inventory_planner(self):
        home = os.environ.get('JAVA_HOME')
        javac = str(Path(home) / 'bin/javac') if home else shutil.which('javac')
        java = str(Path(home) / 'bin/java') if home else shutil.which('java')
        gson = os.environ.get('SHOP_TEST_GSON_JAR')
        if not java or not javac or not gson or not Path(gson).is_file():
            self.skipTest('Java 21 and SHOP_TEST_GSON_JAR required; NeoForge build is separate')
        with tempfile.TemporaryDirectory() as output:
            sources = [JAVA_ROOT / 'ShopTradeProtocol.java', JAVA_ROOT / 'TradeInventoryPlan.java',
                       JAVA_ROOT / 'ListingRequestReceipts.java', JAVA_ROOT / 'ListingAuditWriter.java',
                       ROOT / 'tests/java/ListingRequestReceiptsTest.java', ROOT / 'tests/java/ListingAuditWriterTest.java',
                       JAVA_ROOT.parent / 'delivery/DeliveryProtocol.java', ROOT / 'tests/java/DeliveryProtocolTest.java',
                       JAVA_ROOT.parent / 'mail/MailClaimProtocol.java', ROOT / 'tests/java/MailClaimProtocolTest.java',
                       ROOT / 'tests/java/ShopTradeProtocolTest.java', ROOT / 'tests/java/TradeInventoryPlanTest.java',
                       ROOT / 'tests/java/stubs/net/minecraft/world/item/ItemStack.java']
            subprocess.run([javac, '--release', '21', '-cp', gson, '-d', output, *map(str,sources)], check=True, capture_output=True, text=True)
            for main in ['DeliveryProtocolTest', 'ShopTradeProtocolTest', 'TradeInventoryPlanTest', 'MailClaimProtocolTest', 'ListingRequestReceiptsTest', 'ListingAuditWriterTest']:
                result = subprocess.run([java, '-cp', output + os.pathsep + gson, main], check=True, capture_output=True, text=True)
                self.assertIn('passed', result.stdout)

if __name__ == '__main__':
    unittest.main()
