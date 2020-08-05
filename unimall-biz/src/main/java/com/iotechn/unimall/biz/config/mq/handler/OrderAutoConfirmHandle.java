package com.iotechn.unimall.biz.config.mq.handler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iotechn.unimall.biz.constant.LockConst;
import com.iotechn.unimall.biz.service.order.OrderBizService;
import com.iotechn.unimall.core.exception.AppServiceException;
import com.iotechn.unimall.core.exception.ExceptionDefinition;
import com.iotechn.unimall.data.component.LockComponent;
import com.iotechn.unimall.data.domain.OrderDO;
import com.iotechn.unimall.data.domain.OrderSkuDO;
import com.iotechn.unimall.data.enums.DMQHandlerType;
import com.iotechn.unimall.data.enums.OrderStatusType;
import com.iotechn.unimall.data.mapper.OrderSkuMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

public class OrderAutoConfirmHandle implements RedisNotifyHandler{

    private static final Logger logger = LoggerFactory.getLogger(OrderAutoConfirmHandle.class);

    @Autowired
    private LockComponent lockComponent;

    @Autowired
    private OrderBizService orderBizService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int handle(String orderNo) {
        // 加锁，防止集群时多台服务器同时运行
        if (lockComponent.tryLock(LockConst.ORDER_STATUS_LOCK + orderNo,15)) {
            try {
                OrderDO orderDO = orderBizService.checkOrderExistByNo(orderNo, null).get(0);
                if (orderDO.getStatus() != OrderStatusType.WAIT_CONFIRM.getCode()) {
                    throw new AppServiceException(ExceptionDefinition.ORDER_STATUS_NOT_SUPPORT_CONFIRM);
                }
                OrderDO updateOrderDO = new OrderDO();
                updateOrderDO.setStatus(OrderStatusType.WAIT_APPRAISE.getCode());
                updateOrderDO.setGmtUpdate(new Date());
                orderBizService.changeOrderStatus(orderNo, OrderStatusType.WAIT_CONFIRM.getCode(), updateOrderDO);
                logger.info("[订单自动收货] 订单号：" + orderNo);
            } catch (Exception e) {
                logger.error("[订单自动收货任务] 异常", e);
                return 0;
            } finally {
                lockComponent.release(LockConst.ORDER_STATUS_LOCK + orderNo);
            }
            return 1;
        }
        return 0;
    }

    @Override
    public int getCode() {
        return DMQHandlerType.ORDER_AUTO_RECEIPT.getCode();
    }
}
