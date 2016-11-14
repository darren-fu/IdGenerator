package df.open;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Random;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.logging.Logger;

import static com.sun.org.apache.xalan.internal.lib.ExsltStrings.split;

/**
 * 说明: 生成唯一性ID,类型Long,64位
 * 参考雪花算法
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
public class IdGenerator {

    // 时间基线  2016/1/1
    private final long timeBaseLine = 1454315864414L;

    // 服务编号
    private volatile long serverId = -1;

    //服务实例编号
    private volatile long instanceId = -1;

    private volatile boolean inited = false;

    // 序列号
    private long sequence;

    private static final String ID_CREATOR_KEY = "ID_CREATOR";
    private static final String KEY_SEP = ":";

    private static final long timeBits = 41;
    private static final long serverIdBits = 7;
    private static final long instanceIdBits = 10;
    private static final long sequenceBits = 5;

    private static final long maxServerId = -1L ^ (-1L << serverIdBits);
    private static final long maxInstanceId = -1L ^ (-1L << instanceIdBits);
    private static final long maxSequence = -1L ^ (-1L << sequenceBits);


    private static final long timeBitsShift = serverIdBits + instanceIdBits + sequenceBits;
    private static final long serverIdBitsShift = instanceIdBits + sequenceBits;
    private static final long instanceIdBitsShift = sequenceBits;


    private long lastTimestamp = -1L;
    private static final Random r = new Random();

    private static IdGenerator idGenerator = new IdGenerator();

    private IdGenerator() {
    }


    public static IdGenerator getInstance() {
        return idGenerator;
    }

    /**
     * 应用启动完成后调用init
     *
     * @param serverId
     */
    public synchronized void init(long serverId) {
        this.serverId = serverId;
        if (!inited) {
            inited = true;
            Jedis jedis = new Jedis("localhost", 6379);
            ScheduledExecutorService scheduledService = Executors.newScheduledThreadPool(1);
            RegisterIdCreatorInstanceTask registerIdCreatorInstanceTask = new RegisterIdCreatorInstanceTask(jedis);
            scheduledService.scheduleWithFixedDelay(registerIdCreatorInstanceTask, 0, RegisterIdCreatorInstanceTask.INTERVAL_SECONDS, TimeUnit.SECONDS);
        } else {
            System.out.println("已经初始化！");
        }
    }


    /**
     * 注册id生成器实例
     */
    private class RegisterIdCreatorInstanceTask implements Runnable {
        private Logger logger = Logger.getLogger(RegisterIdCreatorInstanceTask.class.getCanonicalName());

        public static final int INTERVAL_SECONDS = 30;

        private Jedis jedis;

        private RegisterIdCreatorInstanceTask(Jedis jedis) {
            this.jedis = jedis;
        }

        public void run() {

            try {

                long srvId = idGenerator.getServerId();
                long currentInstanceId = idGenerator.getInstanceId();

                String prefixKey = ID_CREATOR_KEY + KEY_SEP + srvId + KEY_SEP;

                if (currentInstanceId < 0) {
                    //注册
                    registerInstanceIdWithIpv4();
                } else {
                    //续约
                    String result = jedis.set(prefixKey + currentInstanceId, srvId + KEY_SEP + currentInstanceId, "XX", "EX", INTERVAL_SECONDS * 3);
                    if (!"OK".equals(result)) {
                        logger.warning("服务[" + srvId + "]ID生成器：" + currentInstanceId + "续约失败，等待重新注册");
                        registerInstanceIdWithIpv4();
                    } else {
                        logger.info("服务[" + srvId + "]ID生成器：" + currentInstanceId + "续约成功");
                    }

                }

            } catch (JedisException e) {
                logger.severe("Redis 出现异常！");
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (idGenerator.getInstanceId() < 0) {
                    idGenerator.setInited(false);
                }
                if (jedis != null) {
                    jedis.close();
                }
            }
        }

        private int registerInstanceIdWithIpv4() {
            long ip4Value = getIp4LongValue();
            // Redis key 格式:key->val , ID_CREATOR:serverId:instanceId -> serverId:instanceId
            String prefixKey = ID_CREATOR_KEY + KEY_SEP + serverId + KEY_SEP;

            // 需要使用java8
            int regInstanceId = registerInstanceId((int) (ip4Value % (maxInstanceId + 1)), (int) maxInstanceId, (v) -> {
                String res = jedis.set(prefixKey + v, serverId + KEY_SEP + v, "NX", "EX", INTERVAL_SECONDS * 3);
                return "OK".equals(res) ? v : -1;
            });

            idGenerator.setInstanceId(regInstanceId);
            idGenerator.setInited(true);

            logger.info("服务[" + serverId + "]注册了一个ID生成器：" + regInstanceId);
            return regInstanceId;
        }


        /**
         * 注册instance,成功就返回
         *
         * @param basePoint
         * @param max
         * @param action
         * @return
         */
        public int registerInstanceId(int basePoint, int max, Function<Integer, Integer> action) {
            int result;
            for (int i = basePoint; i <= max; i++) {
                result = action.apply(i);
                if (result > -1) {
                    return result;
                }
            }

            for (int i = 0; i < basePoint; i++) {
                result = action.apply(i);
                if (result > -1) {
                    return result;
                }
            }
            return 0;
        }

        /**
         * IPV4地址转Long
         *
         * @return
         */
        private long getIp4LongValue() {
            try {
                InetAddress inetAddress = Inet4Address.getLocalHost();
                byte[] ip = inetAddress.getAddress();

                return Math.abs(((0L | ip[0]) << 24)
                        | ((0L | ip[1]) << 16)
                        | ((0L | ip[2]) << 8)
                        | (0L | ip[3]));

            } catch (Exception ex) {
                ex.printStackTrace();
                return 0;
            }
        }
    }


    /**
     * 获取ID
     *
     * @return
     */
    public long getId() {
        long id = nextId();
        return id;
    }

    private synchronized long nextId() {
        if (serverId < 0 || instanceId < 0) {
            throw new IllegalArgumentException("目前不能生成唯一性ID,serverId:[" + serverId + "],instanceId:[" + instanceId + "]!");
        }

        long timestamp = currentTime();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Err clock");
        }
        sequence = (sequence + 1) & maxSequence;
        if (lastTimestamp == timestamp) {
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        }
        lastTimestamp = timestamp;

        long id = ((timestamp - timeBaseLine) << timeBitsShift)
                | (serverId << serverIdBitsShift)
                | (instanceId << instanceIdBitsShift)
                | sequence;
        return id;
    }

    /**
     * get the timestamp (millis second) of id
     *
     * @param id the nextId
     * @return the timestamp of id
     */
    public long getIdTimestamp(long id) {
        return timeBaseLine + (id >> timeBitsShift);
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = currentTime();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTime();
        }
        return timestamp;
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("IdCreator{");
        sb.append("serverId=").append(serverId);
        sb.append(",instanceId=").append(instanceId);
        sb.append(", timeBaseLine=").append(timeBaseLine);
        sb.append(", lastTimestamp=").append(lastTimestamp);
        sb.append(", sequence=").append(sequence);
        sb.append('}');
        return sb.toString();
    }


    public long getServerId() {
        return serverId;
    }

    private void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public long getInstanceId() {
        return instanceId;
    }


    private void setInstanceId(long instanceId) {
        this.instanceId = instanceId;
    }

    public boolean isInited() {
        return inited;
    }

    private void setInited(boolean inited) {
        this.inited = inited;
    }

}
