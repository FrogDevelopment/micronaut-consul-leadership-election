@RequiresConsul
@Requires(property = LeadershipConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE,
          defaultValue = StringUtils.FALSE)
package com.frogdevelopment.micronaut.consul.leadership;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.consul.condition.RequiresConsul;
