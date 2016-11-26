package com.cedarsoftware.util

import com.cedarsoftware.ncube.ApplicationID
import com.cedarsoftware.ncube.Axis
import com.cedarsoftware.ncube.Column
import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.NCubeManager
import com.cedarsoftware.ncube.ReleaseStatus
import com.cedarsoftware.ncube.RuleInfo
import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import com.cedarsoftware.ncube.exception.InvalidCoordinateException
import com.cedarsoftware.ncube.util.VersionComparator
import groovy.transform.CompileStatic
import ncube.grv.method.NCubeGroovyController

import static com.cedarsoftware.util.VisualizerConstants.ALL_GROUPS_KEYS
import static com.cedarsoftware.util.VisualizerConstants.ALL_GROUPS_MAP
import static com.cedarsoftware.util.VisualizerConstants.AXIS_TRAIT
import static com.cedarsoftware.util.VisualizerConstants.BUSINESS_DIVISION_CODE
import static com.cedarsoftware.util.VisualizerConstants.BUSINESS_DIVISION_CUBE_NAME
import static com.cedarsoftware.util.VisualizerConstants.CLASS_TRAITS
import static com.cedarsoftware.util.VisualizerConstants.COMMA_SPACE
import static com.cedarsoftware.util.VisualizerConstants.DATE_TIME_FORMAT
import static com.cedarsoftware.util.VisualizerConstants.DEFAULT_AVAILABLE_SCOPE_KEYS
import static com.cedarsoftware.util.VisualizerConstants.DEFAULT_LEVEL
import static com.cedarsoftware.util.VisualizerConstants.DEFAULT_SCOPE_VALUE
import static com.cedarsoftware.util.VisualizerConstants.DERIVED_SCOPE_KEYS
import static com.cedarsoftware.util.VisualizerConstants.DOT_CLASS_TRAITS
import static com.cedarsoftware.util.VisualizerConstants.DOT_TRAITS
import static com.cedarsoftware.util.VisualizerConstants.DOUBLE_BREAK
import static com.cedarsoftware.util.VisualizerConstants.EFFECTIVE_VERSION
import static com.cedarsoftware.util.VisualizerConstants.EFFECTIVE_VERSION_SCOPE_KEY
import static com.cedarsoftware.util.VisualizerConstants.ENT_APP
import static com.cedarsoftware.util.VisualizerConstants.INDENT
import static com.cedarsoftware.util.VisualizerConstants.LOCATION_STATE
import static com.cedarsoftware.util.VisualizerConstants.MANDATORY_RPM_SCOPE_KEYS
import static com.cedarsoftware.util.VisualizerConstants.MISSING_SCOPE
import static com.cedarsoftware.util.VisualizerConstants.POLICY_CONTROL_DATE
import static com.cedarsoftware.util.VisualizerConstants.QUOTE_DATE
import static com.cedarsoftware.util.VisualizerConstants.RPM_CLASS
import static com.cedarsoftware.util.VisualizerConstants.RPM_CLASS_DOT
import static com.cedarsoftware.util.VisualizerConstants.RPM_ENUM
import static com.cedarsoftware.util.VisualizerConstants.RPM_ENUM_DOT
import static com.cedarsoftware.util.VisualizerConstants.RPM_SCOPE_CLASS_DOT
import static com.cedarsoftware.util.VisualizerConstants.R_RPM_TYPE
import static com.cedarsoftware.util.VisualizerConstants.R_SCOPED_NAME
import static com.cedarsoftware.util.VisualizerConstants.SCOPE_VALUE_NOT_FOUND
import static com.cedarsoftware.util.VisualizerConstants.SOURCE_FIELD_NAME
import static com.cedarsoftware.util.VisualizerConstants.SOURCE_SCOPE_KEY_PREFIX
import static com.cedarsoftware.util.VisualizerConstants.STATE
import static com.cedarsoftware.util.VisualizerConstants.STATE_CUBE_NAME
import static com.cedarsoftware.util.VisualizerConstants.STATUS_MISSING_START_SCOPE
import static com.cedarsoftware.util.VisualizerConstants.STATUS_SUCCESS
import static com.cedarsoftware.util.VisualizerConstants.SYSTEM_SCOPE_KEY_PREFIX
import static com.cedarsoftware.util.VisualizerConstants.UNABLE_TO_LOAD
import static com.cedarsoftware.util.VisualizerConstants.UNSPECIFIED
import static com.cedarsoftware.util.VisualizerConstants.V_ENUM
import static com.cedarsoftware.util.VisualizerConstants.V_MAX
import static com.cedarsoftware.util.VisualizerConstants.V_MIN
import static com.cedarsoftware.util.VisualizerConstants._ENUM

/**
 * Provides the information used to visualize rpm cubes associated with a given rpm cube.
 */

// TODO: This code needs to be moved out of NCE and pulled-in via Grapes.
@CompileStatic
class Visualizer extends NCubeGroovyController
{
	private VisualizerHelper helper = new VisualizerHelper()
	private Set<String> messages = []
	private Set<String> visited = []
	private Map<String, Set<String>> requiredScopeKeys = [:]
	private Map<String, Set<String>> optionalScopeKeys = [:]
	private String defaultScopeEffectiveVersion
	private String defaultScopeDate
	private Deque<VisualizerRelInfo> stack = new ArrayDeque<>()

	/**
	 * Provides the information used to visualize rpm cubes associated with a given rpm cube.
	 *
	 * input:
	 * 	          String startCubeName, name of the starting cube
	 *            Map scope
	 *            Set selectedGroups, indicates which groups should be included in the visualization
	 *            String selectedLevel, indicates the depth of traversal from the start cube
	 *
	 * output     Map containing status, messages and visualizer information
	 *
	 */
	Map buildGraph()
	{
		Map options = input.options as Map
		String cubeName = options.startCubeName as String
		helper.ncube = ncube

		defaultScopeEffectiveVersion = applicationID.version.replace('.', '-')
		defaultScopeDate = DATE_TIME_FORMAT.format(new Date())

		VisualizerInfo visInfo = new VisualizerInfo()
		visInfo.startCubeName = cubeName
		visInfo.scope = options.scope as CaseInsensitiveMap<String, Object>
		visInfo.allGroups = ALL_GROUPS_MAP
		visInfo.availableGroupsAllLevels = [] as Set
		visInfo.groupSuffix = _ENUM
		visInfo.selectedGroups = options.selectedGroups as Set ?: ALL_GROUPS_KEYS
		String selectedLevel = options.selectedLevel as String
		visInfo.selectedLevel = selectedLevel == null ? DEFAULT_LEVEL : Converter.convert(selectedLevel, long.class) as long
		visInfo.availableScopeKeys =  options.availableScopeKeys as Set ?: DEFAULT_AVAILABLE_SCOPE_KEYS
		visInfo.availableScopeValues = options.availableScopeValues as Map ?: loadAvailableScopeValues()
		visInfo.maxLevel = 1
		visInfo.nodeCount = 1
		visInfo.nodes = []
		visInfo.edges = []

		if (hasMissingMinimumScope(visInfo))
		{
			return [status: STATUS_MISSING_START_SCOPE, visInfo: visInfo, message: messages.join(DOUBLE_BREAK)]
		}

		getRpmVisualization(visInfo)

		String message = messages.empty ? null : messages.join(DOUBLE_BREAK)
		return [status: STATUS_SUCCESS, visInfo: visInfo, message: message]
	}

	private void getRpmVisualization(VisualizerInfo visInfo)
	{
		VisualizerRelInfo relInfo = new VisualizerRelInfo()
		relInfo.targetCube = getCube(visInfo.startCubeName)
		relInfo.scope = visInfo.scope
		relInfo.targetLevel = 1
		relInfo.targetId = 1
		stack.push(relInfo)

		while (!stack.empty)
		{
			processCube(visInfo, stack.pop())
		}

		addSets(visInfo.availableScopeKeys, requiredScopeKeys.values() as Set)
		addSets(visInfo.availableScopeKeys, optionalScopeKeys.values() as Set)
        visInfo.trimSelectedLevel()
        visInfo.trimSelectedGroups()
	}

	private static Set<Set> addSets(Set<String> set, Set<Set> sets)
	{
		sets.each {
			set.addAll(it)
		}
		return sets
	}

	private void processCube(VisualizerInfo visInfo, VisualizerRelInfo relInfo)
	{
		if (relInfo.targetCube.name.startsWith(RPM_CLASS))
		{
			processClassCube(visInfo, relInfo)
		}
		else
		{
			processEnumCube(visInfo, relInfo)
		}
	}

	private void processClassCube(VisualizerInfo visInfo, VisualizerRelInfo relInfo)
	{
		String targetCubeName = relInfo.targetCube.name

		boolean loadFieldsAndTraits = canLoadTargetAsRpmClass(relInfo)
		if (loadFieldsAndTraits)
		{
			loadFieldsAndTraits = getTraitMaps(visInfo, relInfo)
		}

		addToEdges(visInfo, relInfo)

		if (!visited.add(targetCubeName + relInfo.scope.toString()))
		{
			return
		}

		relInfo.group = relInfo.getGroupName()
		visInfo.availableGroupsAllLevels << relInfo.group
		addToNodes(visInfo, relInfo)

		if (loadFieldsAndTraits)
		{
			relInfo.targetTraitMaps.each { targetFieldName, targetTraits ->
				if (CLASS_TRAITS != targetFieldName)
				{
					String targetFieldRpmType = targetTraits[R_RPM_TYPE]

					if (!helper.isPrimitive(targetFieldRpmType))
					{
						NCube nextTargetCube
						String nextTargetCubeName = ""
						if (targetTraits.containsKey(V_ENUM))
						{
							nextTargetCubeName = RPM_ENUM_DOT + targetTraits[V_ENUM]
							nextTargetCube = getCube(nextTargetCubeName)
						}
						else if (targetFieldRpmType)
						{
							nextTargetCubeName = RPM_CLASS_DOT + targetFieldRpmType
							nextTargetCube = getCube(nextTargetCubeName)
						}

						if (nextTargetCube)
						{
							addToStack(visInfo, relInfo, nextTargetCube, targetFieldRpmType, targetFieldName)
						}
						else
						{
							messages << "No cube exists with name of ${nextTargetCubeName}. Cube not included in the visualization.".toString()
						}
					}
				}
			}
		}
	}

	private void processEnumCube(VisualizerInfo visInfo, VisualizerRelInfo relInfo)
	{
		relInfo.group = UNSPECIFIED
		String targetCubeName = relInfo.targetCube.name
		String sourceFieldRpmType = relInfo.sourceFieldRpmType

		if (!targetCubeName.startsWith(RPM_ENUM))
		{
			throw new IllegalStateException("Cube is not an rpm.enum cube: ${targetCubeName}.")
		}

		if (relInfo.sourceCube && (!sourceFieldRpmType || helper.isPrimitive(sourceFieldRpmType)))
		{
			return
		}

		boolean loadFieldsAndTraits = getTraitMaps(visInfo, relInfo)

		if (loadFieldsAndTraits)
		{
			relInfo.targetTraitMaps.each { targetFieldName, targetTraits ->
				if (CLASS_TRAITS != targetFieldName)
				{
					try
					{
						String nextTargetCubeName = relInfo.getNextTargetCubeName(targetFieldName)

						if (nextTargetCubeName)
						{
							NCube nextTargetCube = getCube(nextTargetCubeName)
							if (nextTargetCube)
							{
								addToStack(visInfo, relInfo, nextTargetCube, relInfo.sourceFieldRpmType, targetFieldName)

								if (relInfo.group == UNSPECIFIED)
								{
									relInfo.group = relInfo.getGroupName(nextTargetCubeName)
								}
							}
							else
							{
								messages << "No cube exists with name of ${nextTargetCubeName}. Cube not included in the visualization.".toString()
							}
						}
					}
					catch (Exception e)
					{
						throw new IllegalStateException("Error processing the cube for enum field ${targetFieldName} in enum ${targetCubeName}.", e)
					}
				}
			}
		}

		addToEdges(visInfo, relInfo)

		if (!visited.add(targetCubeName + relInfo.scope.toString()))
		{
			return
		}

		visInfo.availableGroupsAllLevels << relInfo.group
		addToNodes(visInfo, relInfo)
	}

	private VisualizerRelInfo addToStack(VisualizerInfo visInfo, VisualizerRelInfo relInfo, NCube nextTargetCube, String rpmType, String targetFieldName)
	{
		try
		{
			NCube nextSourceCube = relInfo.targetCube

			VisualizerRelInfo nextRelInfo = new VisualizerRelInfo()
			nextRelInfo.targetCube = nextTargetCube
			nextRelInfo.scope = getScopeRelativeToSource(nextTargetCube, rpmType, targetFieldName, relInfo.scope)
			nextRelInfo.sourceCube = nextSourceCube
			nextRelInfo.sourceScope = relInfo.targetScope
			nextRelInfo.sourceFieldName = targetFieldName
			nextRelInfo.sourceFieldRpmType = rpmType
			nextRelInfo.sourceTraitMaps = relInfo.targetTraitMaps
			nextRelInfo.sourceId = relInfo.targetId

			long nextTargetTargetLevel = relInfo.targetLevel + 1
			nextRelInfo.targetLevel = nextTargetTargetLevel

			long maxLevel = visInfo.maxLevel
			visInfo.maxLevel = maxLevel < nextTargetTargetLevel ? nextTargetTargetLevel : maxLevel
			visInfo.nodeCount += 1
			nextRelInfo.targetId = visInfo.nodeCount

			stack.push(nextRelInfo)
			return nextRelInfo
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Error processing the class for field ${relInfo.sourceFieldName} in class ${nextTargetCube.name}.", e)
		}
	}

	private boolean canLoadTargetAsRpmClass(VisualizerRelInfo relInfo)
	{
		//When the source cube points directly to the target cube (source cube and target cube are both rpm.class),
		//check if the source field name matches up with the scoped name of the target. If not, the target cube cannot be
		//loaded as an rpm.class.
		NCube sourceCube = relInfo.sourceCube
		NCube targetCube = relInfo.targetCube

		if (sourceCube && sourceCube.name.startsWith(RPM_CLASS_DOT) && targetCube.name.startsWith(RPM_CLASS_DOT) &&
				targetCube.getAxis(AXIS_TRAIT).findColumn(R_SCOPED_NAME))
		{
			String type = relInfo.sourceFieldRpmType
			NCube classTraitsCube = getCube(RPM_SCOPE_CLASS_DOT + type + DOT_CLASS_TRAITS)
			String sourceFieldName = relInfo.sourceFieldName
			if (!classTraitsCube.getAxis(type).findColumn(sourceFieldName))
			{
				relInfo.targetTraitMaps = [(CLASS_TRAITS): [(R_SCOPED_NAME): UNABLE_TO_LOAD]] as Map
				String msg = getLoadTargetAsRpmClassMessage(relInfo, type)
				relInfo.notes << msg
				relInfo.loadFieldsAndTraits = false
				return false
			}
		}
		return true
	}

	private static void addToEdges(VisualizerInfo visInfo, VisualizerRelInfo relInfo)
	{
		NCube sourceCube = relInfo.sourceCube
		if (!sourceCube)
		{
			return
		}

		String sourceFieldName = relInfo.sourceFieldName
		Map<String, Map<String, Object>> sourceTraitMaps = relInfo.sourceTraitMaps

		Map<String, String> edgeMap = [:]
		String sourceCubeEffectiveName = relInfo.getSourceEffectiveName()
		String targetCubeEffectiveName = relInfo.getTargetEffectiveName()
		edgeMap.id = String.valueOf(visInfo.edges.size() + 1)
		edgeMap.from = String.valueOf(relInfo.sourceId)
		edgeMap.to = String.valueOf(relInfo.targetId)
		edgeMap.fromName = sourceCubeEffectiveName
		edgeMap.toName = targetCubeEffectiveName
		edgeMap.fromFieldName = sourceFieldName
		edgeMap.level = String.valueOf(relInfo.targetLevel)
		edgeMap.label = ''
		Map<String, Map<String, Object>> sourceFieldTraitMap = sourceTraitMaps[sourceFieldName] as Map
		String vMin = sourceFieldTraitMap[V_MIN]
		String vMax = sourceFieldTraitMap[V_MAX]

		if (vMin != null && vMax != null)
		{
			edgeMap.title = "${vMin}:${vMax}"
		}
		else
		{
			edgeMap.title = ''
		}
		visInfo.edges << edgeMap
	}

	private static void addToNodes(VisualizerInfo visInfo, VisualizerRelInfo relInfo)
	{
		NCube targetCube = relInfo.targetCube
		String targetCubeName = targetCube.name
		String sourceFieldName = relInfo.sourceFieldName

		Map<String, String> nodeMap = [:]
		nodeMap.id = String.valueOf(relInfo.targetId)
		nodeMap.level = String.valueOf(relInfo.targetLevel)
		nodeMap.name = targetCubeName
		nodeMap.fromFieldName = sourceFieldName == null ? null : sourceFieldName
		nodeMap.label = relInfo.getLabel()
		nodeMap.title = targetCubeName
		nodeMap.desc = relInfo.getTitle()
		nodeMap.group = relInfo.getNodeGroup()
		visInfo.nodes << nodeMap
	}

	/**
	 * Sets the basic scope required to load a target class based on scoped source class, source field name, target class name, and current scope.
	 * Retains all other scope.
	 * If the source class is not a scoped class, returns the scope unchanged.
	 *
	 * @param targetCube String target cube
	 * @param targetTraitsMaps Map fields and traits of the target class
	 * @param sourceTraitsMap Map fields and traits of the source class
	 * @param sourceFieldName String source field name
	 * @param scope Map<String, Object> scope
	 *
	 * @return Map new scope
	 *
	 */
	private static Map<String, Object> getScopeRelativeToSource(NCube targetCube, String sourceFieldRpmType, String targetFieldName, Map scope)
	{
		Map<String, Object> newScope = new CaseInsensitiveMap<String, Object>(scope)

		if (targetCube.name.startsWith(RPM_ENUM))
		{
			newScope[SOURCE_FIELD_NAME] = targetFieldName
		}
		else if (targetCube.getAxis(AXIS_TRAIT).findColumn(R_SCOPED_NAME))
		{
			String newScopeKey = sourceFieldRpmType
			String oldValue = scope[newScopeKey]
			if (oldValue)
			{
				newScope[SOURCE_SCOPE_KEY_PREFIX + sourceFieldRpmType] = oldValue
			}
			newScope[newScopeKey] = targetFieldName
		}
		return newScope
	}

	private Map<String, Object> getDefaultScope(String cubeName)
	{
		String type = getTypeFromCubeName(cubeName)
		Map<String, Object> scope = new CaseInsensitiveMap<String, Object>()
		scope[type] = DEFAULT_SCOPE_VALUE
		scope[EFFECTIVE_VERSION] = defaultScopeEffectiveVersion
		scope[POLICY_CONTROL_DATE] = defaultScopeDate
		scope[QUOTE_DATE] = defaultScopeDate
		return scope
	}

	private boolean hasMissingMinimumScope(VisualizerInfo visInfo)
	{
		boolean hasMissingScope = false
		String cubeName = visInfo.startCubeName
		Map<String, Object> scope = visInfo.scope
		String type = getTypeFromCubeName(cubeName)
		String messageSuffix = "Its default value may be changed as desired."
		String messageScopeValues = getAvailableScopeValuesMessage(visInfo, cubeName, type)
		String messageSuffixType = "Please replace ${DEFAULT_SCOPE_VALUE} for ${type} with an actual scope value."

		if (scope)
		{
			hasMissingScope = addMissingMinimumScope(visInfo, EFFECTIVE_VERSION, defaultScopeEffectiveVersion, messageSuffix) ?: hasMissingScope
			hasMissingScope = addMissingMinimumScope(visInfo, POLICY_CONTROL_DATE, defaultScopeDate, messageSuffix) ?: hasMissingScope
			hasMissingScope = addMissingMinimumScope(visInfo, QUOTE_DATE, defaultScopeDate, messageSuffix) ?: hasMissingScope
			hasMissingScope = addMissingMinimumScope(visInfo, type, DEFAULT_SCOPE_VALUE, messageSuffixType + messageScopeValues) ?: hasMissingScope
		}
		else
		{
			hasMissingScope = true
			Map<String, Object> defaultScope = getDefaultScope(cubeName)
			visInfo.scope = defaultScope
			String msg = getMissingMinimumScopeMessage(defaultScope, messageScopeValues, messageSuffixType )
			messages << msg
		}
		return hasMissingScope
	}

	private boolean addMissingMinimumScope(VisualizerInfo visInfo, String key, String value, String messageSuffix)
	{
		Map<String, Object> scope = visInfo.scope
		boolean missingScope
		if (scope.containsKey(key))
		{
			if (!scope[key])
			{
				visInfo.scope[key] = value
				missingScope = true
			}
			else if (DEFAULT_SCOPE_VALUE == scope[key])
			{
				missingScope = true
			}
		}
		else
		{
			visInfo.scope[key] = value
			missingScope = true
		}

		if (missingScope)
		{
			messages << "Scope is required for ${key}. ${messageSuffix}".toString()
		}
		return missingScope
	}

	private void getRequiredAndOptionalScopeKeys(VisualizerRelInfo relInfo)
	{
		NCube cube = relInfo.targetCube
		String cubeName = cube.name
		if (requiredScopeKeys.containsKey(cubeName))
		{
			relInfo.requiredScopeKeys = requiredScopeKeys[cubeName]
			relInfo.optionalScopeKeys = optionalScopeKeys[cubeName]
		}
		else
		{
			relInfo.requiredScopeKeys = relInfo.requiredScope
			relInfo.optionalScopeKeys = cube.getOptionalScope(relInfo.scope, [:])
			requiredScopeKeys[cubeName] = relInfo.requiredScopeKeys
			optionalScopeKeys[cubeName] = relInfo.optionalScopeKeys
		}
	}

	private boolean getTraitMaps(VisualizerInfo visInfo, VisualizerRelInfo relInfo)
	{
		try
		{
			getTraitMaps(relInfo)
            return true
		}
		catch (Exception e)
		{
			relInfo.loadFieldsAndTraits = false
			Throwable t = getDeepestException(e)
			if (t instanceof InvalidCoordinateException)
			{
				handleInvalidCoordinateException(t as InvalidCoordinateException, visInfo, relInfo)
			}
			else if (t instanceof CoordinateNotFoundException)
			{
				handleCoordinateNotFoundException(t as CoordinateNotFoundException, visInfo, relInfo)
			}
			else
			{
				handleException(t, relInfo)
			}
			return false
		}
	}

	private void handleCoordinateNotFoundException(CoordinateNotFoundException e, VisualizerInfo visInfo, VisualizerRelInfo relInfo)
	{
		String cubeName = e.cubeName
		String axisName = e.axisName
		Object value = e.value ?: 'null'

		if (cubeName && axisName)
		{
			String msg = getCoordinateNotFoundMessage(visInfo, relInfo, axisName, value, cubeName)
			relInfo.notes << msg
			messages << msg
			relInfo.targetTraitMaps = [(CLASS_TRAITS): [(R_SCOPED_NAME): SCOPE_VALUE_NOT_FOUND]] as Map
		}
		else
		{
			handleException(e as Exception, relInfo)
		}
	}

	private static String getSourceMessage(VisualizerRelInfo relInfo)
	{
		if (relInfo.sourceTraitMaps)
		{
			String sourceScopedName = relInfo.getSourceScopedName()
			return sourceScopedName ? ", the target of ${sourceScopedName} on ${relInfo.sourceCube.name}" : ""
		}
		return ''
	}

	private void handleInvalidCoordinateException(InvalidCoordinateException e, VisualizerInfo visInfo, VisualizerRelInfo relInfo)
	{
		String cubeName = e.cubeName
		Set requiredKeys = e.requiredKeys
		Set<String> missingScope = findMissingScope(relInfo.scope, requiredKeys)

		if (missingScope)
		{
			Map<String, Object> expandedScope = new CaseInsensitiveMap<String, Object>(visInfo.scope)
			missingScope.each { key ->
				expandedScope[key] = DEFAULT_SCOPE_VALUE
			}
			visInfo.scope = expandedScope
			relInfo.targetTraitMaps = [(CLASS_TRAITS): [(R_SCOPED_NAME): MISSING_SCOPE]] as Map
			String msg = getInvalidCoordinateExceptionMessage(visInfo, relInfo, missingScope, cubeName)
			relInfo.notes << msg
			messages << msg
		}
		else
		{
			throw new IllegalStateException("An InvalidCoordinateException was thrown, but no missing scope keys found for ${relInfo.targetCube.name} and scope ${visInfo.scope.toString()}.", e)
		}
	}

	private static Set<String> findMissingScope(Map<String, Object> scope, Set<String> requiredKeys)
	{
		Set<String> missingScope = []
		requiredKeys.each { key ->
			if (!MANDATORY_RPM_SCOPE_KEYS.contains(key) && (scope == null || !scope.containsKey(key)))
			{
				missingScope << key
			}
		}
		return missingScope.size() > 0 ? missingScope : null
	}

	private void handleException(Throwable e, VisualizerRelInfo relInfo)
	{
		Throwable t = getDeepestException(e)
		String msg = getExceptionMessage(relInfo, e, t)
		relInfo.notes << msg
		messages << msg
		relInfo.targetTraitMaps = [(CLASS_TRAITS): [(R_SCOPED_NAME): UNABLE_TO_LOAD]] as Map
	}

	private static Throwable getDeepestException(Throwable e)
	{
		while (e.cause != null)
		{
			e = e.cause
		}
		return e
	}

	private void getTraitMaps(VisualizerRelInfo relInfo)
	{
		NCube cube = relInfo.targetCube
		Map<String, Object> scope = relInfo.scope
		relInfo.targetTraitMaps = [:]
		Map<String, Map<String, Object>> traitMaps = relInfo.targetTraitMaps
		Map output = [:]
		if (cube.name.startsWith(RPM_ENUM))
		{
			helper.loadRpmClassFields(RPM_ENUM, cube.name - RPM_ENUM_DOT, scope, traitMaps, output)
		}
		else
		{
			helper.loadRpmClassFields(RPM_CLASS, cube.name - RPM_CLASS_DOT, scope, traitMaps, output)
		}
		relInfo.removeNotExistsFields()
		retainUsedScope(relInfo, output)
	}

	private void retainUsedScope(VisualizerRelInfo relInfo, Map output)
	{
		getRequiredAndOptionalScopeKeys(relInfo)
		Set scopeCollector = []
		scopeCollector.addAll(relInfo.requiredScopeKeys)
		scopeCollector.addAll(relInfo.optionalScopeKeys)
		scopeCollector << EFFECTIVE_VERSION_SCOPE_KEY

        RuleInfo ruleInfo = NCube.getRuleInfo(output)
        Set keysUsed = ruleInfo.getInputKeysUsed()
		scopeCollector.addAll(keysUsed)

		relInfo.targetScope = new CaseInsensitiveMap(relInfo.scope)
		cullScope(relInfo.targetScope, scopeCollector)
	}

	private static void cullScope(Map<String, Object> scope, Set scopeCollector)
	{
		Set keySet = scope.keySet()
		Iterator<String> i = keySet.iterator()
		while (i.hasNext())
		{
			String scopeKey = i.next()
			if (!(scopeCollector.contains(scopeKey) || scopeKey.startsWith(SYSTEM_SCOPE_KEY_PREFIX)))
			{
				i.remove()
			}
		}
	}

	private static String getTypeFromCubeName(String cubeName)
	{
		return (cubeName - RPM_CLASS_DOT)
	}

	private Set<Object> loadAvailableScopeValues(VisualizerInfo visInfo, String cubeName, String key)
	{
		Set<Object> values = getColumnValues(applicationID, cubeName, key)
		visInfo.availableScopeValues[key] = values
		return values
	}

	private Map<String, Set<Object>> loadAvailableScopeValues()
	{
		Map<String, Set<Object>> valuesByKey = new CaseInsensitiveMap<String, Set<Object>>()

		//Values for Risk, SourceRisk, Coverage, SourceCoverage, etc.
		DERIVED_SCOPE_KEYS.each { key ->
			String cubeName = RPM_SCOPE_CLASS_DOT + key + DOT_TRAITS
			Set<Object> values = getColumnValues(applicationID, cubeName, key)
			valuesByKey[key] = values
			valuesByKey[SOURCE_SCOPE_KEY_PREFIX + key] = values
		}

		//Values for effective version
		valuesByKey[EFFECTIVE_VERSION] = getAllVersions(applicationID.tenant, applicationID.app) as Set<Object>

		//Values from ENT.APP
		String latest = NCubeManager.getLatestVersion(ApplicationID.DEFAULT_TENANT, ENT_APP, ReleaseStatus.RELEASE.name())
		ApplicationID entAppAppId = new ApplicationID(ApplicationID.DEFAULT_TENANT, ENT_APP, latest, ReleaseStatus.RELEASE.name(), ApplicationID.HEAD)

   		valuesByKey[BUSINESS_DIVISION_CODE] = getColumnValues(entAppAppId, BUSINESS_DIVISION_CUBE_NAME, BUSINESS_DIVISION_CODE)
		Set<Object> stateValues = getColumnValues(entAppAppId, STATE_CUBE_NAME, STATE)
		valuesByKey[STATE] = stateValues
		valuesByKey[LOCATION_STATE] = stateValues

		return valuesByKey
	}

	private static Set<Object> getColumnValues(ApplicationID applicationID, String cubeName, String axisName)
	{
		NCube cube = NCubeManager.getCube(applicationID, cubeName)
        Set values = new LinkedHashSet<Object>()
        Axis axis = cube.getAxis(axisName)
        if (axis)
        {
            for (Column column : axis.columnsWithoutDefault)
            {
                values.add(column.value)
            }
        }
		return values
	}

	private static Set<String> getAllVersions(String tenant, String app)
	{
		Map<String, List<String>> versionsMap = NCubeManager.getVersions(tenant, app)
		Set<String> versions = new TreeSet<>(new VersionComparator())
		versions.addAll(versionsMap[ReleaseStatus.RELEASE.name()])
		versions.addAll(versionsMap[ReleaseStatus.SNAPSHOT.name()])
		return versions
	}

	private String getAvailableScopeValuesMessage(VisualizerInfo visInfo, String cubeName, String key)
	{
		String messageScopeValues = ''
		Set<Object> scopeValues = visInfo.availableScopeValues[key] ?: loadAvailableScopeValues(visInfo, cubeName, key)
		if (scopeValues) {
			messageScopeValues = """\
${DOUBLE_BREAK}The following values are available for ${key}: \
${DOUBLE_BREAK}${scopeValues.join(COMMA_SPACE)}  """
		}
		return messageScopeValues
	}

	private static String getMissingMinimumScopeMessage(Map<String, Object> scope, String messageScopeValues, String messageSuffixType )
	{
		"""\
The scope for the following scope keys was added since it was required: \
${DOUBLE_BREAK}${INDENT}${scope.keySet().join(COMMA_SPACE)}\
${DOUBLE_BREAK}${messageSuffixType} The other default scope values may also be changed as desired.\
${messageScopeValues}"""
	}

	private static String getExceptionMessage(VisualizerRelInfo relInfo, Throwable e, Throwable t)
	{
		"""\
An exception was thrown while loading fields and traits for ${relInfo.targetCube.name}${getSourceMessage(relInfo)}. \
${DOUBLE_BREAK}<b>Message:</b> ${DOUBLE_BREAK}${e.message}${DOUBLE_BREAK}<b>Root cause: </b>\
${DOUBLE_BREAK}${t.toString()}${DOUBLE_BREAK}<b>Stack trace: </b>${DOUBLE_BREAK}${t.stackTrace.toString()}"""
	}

	private String getCoordinateNotFoundMessage(VisualizerInfo visInfo, VisualizerRelInfo relInfo, String key, Object value, String cubeName)
	{
		String messageScopeValues = getAvailableScopeValuesMessage(visInfo, cubeName, key)
		"""\
The scope value ${value} for scope key ${key} cannot be found on axis ${key} in \
cube ${cubeName}${getSourceMessage(relInfo)}. Please supply a different value for ${key}.\
${messageScopeValues}"""
	}

	private String getInvalidCoordinateExceptionMessage(VisualizerInfo visInfo, VisualizerRelInfo relInfo, Set<String> missingScope, String cubeName)
	{
		StringBuilder message = new StringBuilder()
		message.append("""\
Additional scope is required to load ${cubeName}${getSourceMessage(relInfo)}. Please add scope \
value(s) for the following scope key(s): ${missingScope.join(COMMA_SPACE)}.""")

		missingScope.each{ key ->
			message.append(getAvailableScopeValuesMessage(visInfo, cubeName, key))
		}
		return message.toString()
	}

	private static String getLoadTargetAsRpmClassMessage(VisualizerRelInfo relInfo, String type) {
		"""\
The source ${relInfo.sourceCube.name} points directly to target ${relInfo.targetCube.name} via field ${relInfo.sourceFieldName}, but \
there is no ${type} named ${relInfo.sourceFieldName} on this cube.  ${DOUBLE_BREAK}Therefore \
it cannot be loaded as an rpm.class in the visualization."""
	}
}