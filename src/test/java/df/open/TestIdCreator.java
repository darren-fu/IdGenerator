package df.open;

import org.junit.Test;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 说明:
 * <p/>
 * Copyright: Copyright (c)
 * <p/>
 * Company:
 * <p/>
 *
 * @author darren-fu
 * @version 1.0.0
 * @contact 13914793391
 * @date 2016/11/10
 */
public class TestIdCreator {


    @Test
    public void testIp() throws UnknownHostException {
        InetAddress inetAddress = Inet4Address.getLocalHost();
        byte[] ip = inetAddress.getAddress();

        long ipv = Math.abs((ip[0] << 24)
                | (ip[1] << 16)
                | (ip[2] << 8)
                | ip[3]);
        System.out.println(ipv);
    }


    @Test
    public void testIdCreator() throws InterruptedException {
        IdGenerator idGenerator = IdGenerator.getInstance();

        idGenerator.init(30);
        idGenerator.init(30);

        for (int i = 0; i < 10; i++) {

            try {
                System.out.println(idGenerator.getId());
            } catch (Exception e) {
                e.printStackTrace();
            }
            Thread.sleep(100);
        }
        Thread.sleep(1000);
        for (int i = 0; i < 10; i++) {
            System.out.println(idGenerator.getId());
        }

    }
}
