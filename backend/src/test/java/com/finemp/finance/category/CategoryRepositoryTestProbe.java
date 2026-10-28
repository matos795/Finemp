package com.finemp.finance.category;

import java.util.function.Consumer;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Real Spring Data queries/writes continue through their original proxy; no callRealMethod on an interface. */
@TestConfiguration(proxyBeanMethods=false)
class CategoryRepositoryTestProbe {
    static final class Probe {
        volatile Runnable afterSearch;
        volatile Consumer<FinancialCategory> beforeSave;
        void reset() {afterSearch=null;beforeSave=null;}
    }
    @Bean static Probe categoryProbe() {return new Probe();}
    @Bean static BeanPostProcessor categoryProbePostProcessor(Probe probe) {
        return new BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean,String name) {
                if(!(bean instanceof FinancialCategoryRepository))return bean;
                var factory=new ProxyFactory(bean);
                factory.setInterfaces(FinancialCategoryRepository.class);
                factory.addAdvice((MethodInterceptor)call->{
                    var before=probe.beforeSave;
                    if(before!=null && call.getMethod().getName().equals("saveAndFlush"))before.accept((FinancialCategory)call.getArguments()[0]);
                    Object result=call.proceed();
                    var after=probe.afterSearch;
                    if(after!=null && call.getMethod().getName().equals("search"))after.run();
                    return result;
                });
                return factory.getProxy();
            }
        };
    }
}
