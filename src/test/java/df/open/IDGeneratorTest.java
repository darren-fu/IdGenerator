package df.open;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 说明:
 * <p/>
 * Copyright: Copyright (c)
 * <p/>
 * Company: 江苏千米网络科技有限公司
 * <p/>
 *
 * @author 付亮(OF2101)
 * @version 1.0.0
 * @date 2016/10/9
 */
public class IDGeneratorTest {
    public static void main(String[] args) {
        final long idepo = System.currentTimeMillis() - 3600 * 1000L;
        IdGenerator iw = new IdGenerator(1, 0, idepo);
        IdGenerator iw2 = new IdGenerator(idepo);
        for (int i = 0; i < 10; i++) {
            System.out.println(iw.getId() + " -> " + iw2.getId());
        }
        System.out.println(iw);
        System.out.println(iw2);
        long nextId = iw.getId();
        System.out.println(nextId);
        long time = iw.getIdTimestamp(nextId);
        System.out.println(time + " -> " + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date(time)));


        System.out.println("check speed....");
        IdGenerator iw3 = new IdGenerator(idepo);

        long st = System.currentTimeMillis();
        final int max = 100000;
        for (int i = 0; i < max; i++) {
            iw.getId();
        }
        long et = System.currentTimeMillis();
        System.out.println(1000 * max / (et - st) + "/s");


    }
}
