package com.quancheng.boot.saluki.starter.runner;

import java.lang.reflect.Field;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;

import com.google.common.base.Preconditions;
import com.quancheng.boot.saluki.starter.SalukiReference;
import com.quancheng.boot.saluki.starter.autoconfigure.SalukiProperties;
import com.quancheng.saluki.core.config.ReferenceConfig;
import com.quancheng.saluki.core.utils.ReflectUtil;

import io.grpc.stub.AbstractStub;

public class SalukiReferenceRunner extends InstantiationAwareBeanPostProcessorAdapter {

    private static final Logger    logger = LoggerFactory.getLogger(SalukiReferenceRunner.class);

    private final SalukiProperties grpcProperties;

    public SalukiReferenceRunner(SalukiProperties grpcProperties){
        this.grpcProperties = grpcProperties;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> searchType = bean.getClass();
        while (!Object.class.equals(searchType) && searchType != null) {
            Field[] fields = searchType.getDeclaredFields();
            for (Field field : fields) {
                SalukiReference reference = field.getAnnotation(SalukiReference.class);
                if (reference != null) {
                    Object value = refer(reference, field.getType());
                    try {
                        if (!field.isAccessible()) {
                            field.setAccessible(true);
                        }
                        field.set(bean, value);
                    } catch (Throwable e) {
                        logger.error("Failed to init remote service reference at filed " + field.getName()
                                     + " in class " + bean.getClass().getName() + ", cause: " + e.getMessage(), e);
                    }
                }
            }
            searchType = searchType.getSuperclass();
        }
        return bean;
    }

    private Object refer(SalukiReference reference, Class<?> referenceClass) {
        ReferenceConfig referenceConfig = new ReferenceConfig();
        if (StringUtils.isNoneBlank(reference.group())) {
            referenceConfig.setGroup(reference.group());
        } else {
            String group = grpcProperties.getReferenceGroup();
            Preconditions.checkNotNull(group, "Group can not be null", group);
            referenceConfig.setGroup(group);
        }
        if (StringUtils.isNoneBlank(reference.version())) {
            referenceConfig.setVersion(reference.version());
        } else {
            String version = grpcProperties.getReferenceVersion();
            Preconditions.checkNotNull(version, "Version can not be null", version);
            referenceConfig.setVersion(version);
        }
        String interfaceName = reference.service();
        Preconditions.checkNotNull(interfaceName, "interfaceName can not be null", interfaceName);
        referenceConfig.setInterfaceName(interfaceName);
        referenceConfig.setRegistryName("consul");
        String registryAddress = grpcProperties.getConsulIp();
        Preconditions.checkNotNull(registryAddress, "RegistryAddress can not be null", registryAddress);
        referenceConfig.setRegistryAddress(registryAddress);
        int registryPort = grpcProperties.getConsulPort();
        Preconditions.checkState(registryPort != 0, "RegistryPort can not be null", registryPort);
        referenceConfig.setRegistryPort(registryPort);
        referenceConfig.setInjvm(reference.localProcess());
        referenceConfig.setAsync(reference.callType() == 1 ? true : false);
        referenceConfig.setRequestTimeout(reference.requestTime());
        try {
            Class<?> interfaceClass = ReflectUtil.name2class(interfaceName);
            if (!interfaceClass.isAssignableFrom(referenceClass)) {
                referenceConfig.setGeneric(true);
            } else {
                referenceConfig.setGeneric(false);
            }
        } catch (ClassNotFoundException e) {
            referenceConfig.setGeneric(false);
        }
        if (AbstractStub.class.isAssignableFrom(referenceClass)) {
            referenceConfig.setGrpcStub(true);
            referenceConfig.setInterfaceClass(referenceClass);
        }
        Object value = referenceConfig.get();
        return value;
    }

}