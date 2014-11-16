/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.yarn.container;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.OrderComparator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.yarn.listener.ContainerStateListener.ContainerState;

/**
 * Default implementation of a {@link YarnContainer}.
 *
 * @author Janne Valkealahti
 *
 */
public class DefaultYarnContainer extends AbstractYarnContainer implements BeanFactoryAware {

	private final static Log log = LogFactory.getLog(DefaultYarnContainer.class);

	private ListableBeanFactory beanFactory;

	@Override
	protected void runInternal() {
		Exception runtimeException = null;
		List<Object> results = new ArrayList<Object>();

		try {
			Map<String, ContainerHandler> handlers = beanFactory.getBeansOfType(ContainerHandler.class);
			List<ContainerHandler> orderHandlers = orderHandlers(handlers);
			for (ContainerHandler handler : orderHandlers) {
				results.add(handler.handle(this));
			}
		} catch (Exception e) {
			runtimeException = e;
			log.error("Error handling container", e);
		}

		log.info("Container state based on method results=[" + StringUtils.arrayToCommaDelimitedString(results.toArray())
				+ "] runtimeException=[" + runtimeException + "]");

		if (runtimeException != null) {
			notifyContainerState(ContainerState.FAILED, runtimeException);
		} else if (!isEmptyValues(results)) {
			if (results.size() == 1) {
				notifyContainerState(ContainerState.COMPLETED, results.get(0));
			} else {
				notifyContainerState(ContainerState.COMPLETED, results);
			}
		} else {
			notifyCompleted();
		}
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		Assert.isInstanceOf(ListableBeanFactory.class, beanFactory, "Beanfactory must be of type ListableBeanFactory");
		this.beanFactory = (ListableBeanFactory) beanFactory;
	}

	@Override
	public boolean isWaitCompleteState() {
		// we need to tell boot ContainerLauncherRunner that we're
		// about to notify state via events so it should wait
		return true;
	}

	/**
	 * Returns ordered list of {@link ContainerHandler}s.
	 *
	 * @param handlers the handlers
	 * @return the list ordered list
	 */
	private List<ContainerHandler> orderHandlers(Map<String, ContainerHandler> handlers) {
		List<ContainerHandler> handlersList = new ArrayList<ContainerHandler>(handlers.values());
		OrderComparator comparator = new OrderComparator();
		Collections.sort(handlersList, comparator);
		return handlersList;
	}

	/**
	 * Checks if list contains just null objects or
	 * empty strings.
	 *
	 * @param results the results
	 * @return true, if is empty values
	 */
	private boolean isEmptyValues(List<Object> results) {
		for (Object o : results) {
			if (o != null) {
				if (o instanceof String) {
					if (StringUtils.hasText((String)o)) {
						return false;
					}
				} else {
					return false;
				}
			}
		}
		return true;
	}

}
