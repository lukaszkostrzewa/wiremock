/*
 * Copyright (C) 2011 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.tomakehurst.wiremock.stubbing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize.Inclusion;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.matching.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.matching.ValuePattern.TO_STRING_VALUE_PATTERN;
import static com.google.common.collect.FluentIterable.from;

@JsonSerialize(include=Inclusion.NON_NULL)
@JsonPropertyOrder({ "uuid", "request", "newRequest", "response" })
public class StubMapping {
	
	public static final int DEFAULT_PRIORITY = 5; 

	private UUID uuid = UUID.randomUUID();

	private RequestPattern request;
	private NewRequestPattern newRequest;

	private ResponseDefinition response;
	private Integer priority;
	private String scenarioName;
	private String requiredScenarioState;
	private String newScenarioState;
	private Scenario scenario;

	private long insertionIndex;
    private boolean isTransient = true;

	public StubMapping(RequestPattern requestPattern, ResponseDefinition response) {
		this.request = requestPattern;
		this.response = response;
	}
	
	public StubMapping() {
		//Concession to Jackson
	}
	
	public static final StubMapping NOT_CONFIGURED =
	    new StubMapping(new RequestPattern(), ResponseDefinition.notConfigured());

    public static StubMapping buildFrom(String mappingSpecJson) {
        return Json.read(mappingSpecJson, StubMapping.class);
    }

    public static String buildJsonStringFor(StubMapping mapping) {
		return Json.write(mapping);
	}

	public UUID getUuid() {
		return uuid;
	}

	@VisibleForTesting
	public void setUuid(UUID uuid) {
		this.uuid = uuid;
	}

    public RequestPattern getRequest() {
		return request;
	}
	
	public ResponseDefinition getResponse() {
		return response;
	}
	
	public void setRequest(RequestPattern request) {
		this.request = request;
		this.newRequest = new NewRequestPattern(
            UrlPattern.fromOneOf(request.getUrl(), request.getUrlPattern(), request.getUrlPath(), request.getUrlPathPattern()),
            request.getMethod(),
            toMultiValuePatternMap(request.getHeaders()),
            toMultiValuePatternMap(request.getQueryParameters()),
            toStringValuePatternMap(request.getCookies()),
            toStringValueList(request)
		);
	}

    private Map<String, StringValuePattern> toStringValuePatternMap(Map<String, ValuePattern> valuePatternMap) {
        if (valuePatternMap == null) {
            return null;
        }

        return Maps.transformValues(valuePatternMap, TO_STRING_VALUE_PATTERN);
    }

    private static List<StringValuePattern> toStringValueList(RequestPattern request) {
        return (request.getBodyPatterns() != null) ?
            from(request.getBodyPatterns()).transform(TO_STRING_VALUE_PATTERN).toList() :
            null;
    }

    private static Map<String, MultiValuePattern> toMultiValuePatternMap(Map<String, ValuePattern> valuePatternMap) {
        if (valuePatternMap == null) {
            return null;
        }

        return Maps.transformValues(valuePatternMap, new Function<ValuePattern, MultiValuePattern>() {
            @Override
            public MultiValuePattern apply(ValuePattern input) {
                return new MultiValuePattern(input.toStringValuePattern());
            }
        });
    }

	public void setResponse(ResponseDefinition response) {
		this.response = response;
	}

    public NewRequestPattern getNewRequest() {
        return newRequest;
    }

    public void setNewRequest(NewRequestPattern newRequest) {
        this.newRequest = newRequest;
    }

    @Override
	public String toString() {
		return Json.write(this);
	}

	@JsonIgnore
	public long getInsertionIndex() {
		return insertionIndex;
	}

	@JsonIgnore
	public void setInsertionIndex(long insertionIndex) {
		this.insertionIndex = insertionIndex;
	}

    /**
     * @return True if this StubMapping is not persisted to the file system, false otherwise.
     */
    @JsonIgnore
    public boolean isTransient() {
        return isTransient;
    }

    @JsonIgnore
    public void setTransient(boolean isTransient) {
        this.isTransient = isTransient;
    }

	public Integer getPriority() {
		return priority;
	}

	public void setPriority(Integer priority) {
		this.priority = priority;
	}
	
	public String getScenarioName() {
		return scenarioName;
	}

	public void setScenarioName(String scenarioName) {
		this.scenarioName = scenarioName;
	}

	public String getRequiredScenarioState() {
		return requiredScenarioState;
	}

	public void setRequiredScenarioState(String requiredScenarioState) {
		this.requiredScenarioState = requiredScenarioState;
	}

	public String getNewScenarioState() {
		return newScenarioState;
	}

	public void setNewScenarioState(String newScenarioState) {
		this.newScenarioState = newScenarioState;
	}
	
	public void updateScenarioStateIfRequired() {
		if (isInScenario() && modifiesScenarioState()) {
			scenario.setState(newScenarioState);
		}
	}

	@JsonIgnore
	public Scenario getScenario() {
		return scenario;
	}

	@JsonIgnore
	public void setScenario(Scenario scenario) {
		this.scenario = scenario;
	}

	@JsonIgnore
	public boolean isInScenario() {
		return scenarioName != null;
	}
	
	@JsonIgnore
	public boolean modifiesScenarioState() {
		return newScenarioState != null;
	}
	
	@JsonIgnore
	public boolean isIndependentOfScenarioState() {
		return !isInScenario() || requiredScenarioState == null;
	}
	
	@JsonIgnore
	public boolean requiresCurrentScenarioState() {
		return !isIndependentOfScenarioState() && requiredScenarioState.equals(scenario.getState());
	}
	
	public int comparePriorityWith(StubMapping otherMapping) {
		int thisPriority = priority != null ? priority : DEFAULT_PRIORITY;
		int otherPriority = otherMapping.priority != null ? otherMapping.priority : DEFAULT_PRIORITY;
		return thisPriority - otherPriority;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		StubMapping that = (StubMapping) o;
		return Objects.equals(insertionIndex, that.insertionIndex) &&
				Objects.equals(isTransient, that.isTransient) &&
				Objects.equals(request, that.request) &&
				Objects.equals(response, that.response) &&
				Objects.equals(priority, that.priority) &&
				Objects.equals(scenarioName, that.scenarioName) &&
				Objects.equals(requiredScenarioState, that.requiredScenarioState) &&
				Objects.equals(newScenarioState, that.newScenarioState) &&
				Objects.equals(scenario, that.scenario);
	}

	@Override
	public int hashCode() {
		return Objects.hash(request, response, priority, scenarioName, requiredScenarioState, newScenarioState, scenario, insertionIndex, isTransient);
	}
}
