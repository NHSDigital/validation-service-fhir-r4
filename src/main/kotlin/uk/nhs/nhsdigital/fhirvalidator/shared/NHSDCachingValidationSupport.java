package uk.nhs.nhsdigital.fhirvalidator.shared;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.TranslateConceptResults;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.context.support.ValueSetExpansionOptions;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.hl7.fhir.common.hapi.validation.support.BaseValidationSupportWrapper;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NHSDCachingValidationSupport extends BaseValidationSupportWrapper implements IValidationSupport {
    private static final Logger ourLog = LoggerFactory.getLogger(CachingValidationSupport.class);
    public static final ValueSetExpansionOptions EMPTY_EXPANSION_OPTIONS = new ValueSetExpansionOptions();
    private final Cache<String, Object> myCache;
    private final Cache<String, Object> myValidateCodeCache;
    private final Cache<TranslateCodeRequest, Object> myTranslateCodeCache;
    private final Cache<String, Object> myLookupCodeCache;
    private final ThreadPoolExecutor myBackgroundExecutor;
    private final Map myNonExpiringCache;
    private final Cache<String, Object> myExpandValueSetCache;

    public NHSDCachingValidationSupport(IValidationSupport theWrap) {
        this(theWrap, CachingValidationSupport.CacheTimeouts.defaultValues());
    }

    public NHSDCachingValidationSupport(IValidationSupport theWrap, CachingValidationSupport.CacheTimeouts theCacheTimeouts) {
        super(theWrap.getFhirContext(), theWrap);
        this.myExpandValueSetCache = Caffeine.newBuilder().expireAfterWrite(theCacheTimeouts.getExpandValueSetMillis(), TimeUnit.MILLISECONDS).maximumSize(100L).build();
        this.myValidateCodeCache = Caffeine.newBuilder().expireAfterWrite(theCacheTimeouts.getValidateCodeMillis(), TimeUnit.MILLISECONDS).maximumSize(5000L).build();
        this.myLookupCodeCache = Caffeine.newBuilder().expireAfterWrite(theCacheTimeouts.getLookupCodeMillis(), TimeUnit.MILLISECONDS).maximumSize(5000L).build();
        this.myTranslateCodeCache = Caffeine.newBuilder().expireAfterWrite(theCacheTimeouts.getTranslateCodeMillis(), TimeUnit.MILLISECONDS).maximumSize(5000L).build();
        this.myCache = Caffeine.newBuilder().expireAfterWrite(theCacheTimeouts.getMiscMillis(), TimeUnit.MILLISECONDS).maximumSize(5000L).build();
        this.myNonExpiringCache = Collections.synchronizedMap(new HashMap());
        LinkedBlockingQueue<Runnable> executorQueue = new LinkedBlockingQueue(1000);
        BasicThreadFactory threadFactory = (new BasicThreadFactory.Builder()).namingPattern("CachingValidationSupport-%d").daemon(false).priority(5).build();
        this.myBackgroundExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, executorQueue, threadFactory, new ThreadPoolExecutor.DiscardPolicy());
    }

    public List fetchAllConformanceResources() {
        String key = "fetchAllConformanceResources";
        return (List)this.loadFromCacheWithAsyncRefresh(this.myCache, key, (t) -> {
            return super.fetchAllConformanceResources();
        });
    }

    public <T extends IBaseResource> List fetchAllStructureDefinitions() {
        String key = "fetchAllStructureDefinitions";
        return (List)this.loadFromCacheWithAsyncRefresh(this.myCache, key, (t) -> {
            return super.fetchAllStructureDefinitions();
        });
    }

    public <T extends IBaseResource> List fetchAllNonBaseStructureDefinitions() {
        String key = "fetchAllNonBaseStructureDefinitions";
        return (List)this.loadFromCacheWithAsyncRefresh(this.myCache, key, (t) -> {
            return super.fetchAllNonBaseStructureDefinitions();
        });
    }

    public IBaseResource fetchCodeSystem(String theSystem) {
        return (IBaseResource)this.loadFromCache(this.myCache, "fetchCodeSystem " + theSystem, (t) -> {
            return super.fetchCodeSystem(theSystem);
        });
    }

    public IBaseResource fetchValueSet(String theUri) {
        return (IBaseResource)this.loadFromCache(this.myCache, "fetchValueSet " + theUri, (t) -> {
            return super.fetchValueSet(theUri);
        });
    }

    public IBaseResource fetchStructureDefinition(String theUrl) {
        return (IBaseResource)this.loadFromCache(this.myCache, "fetchStructureDefinition " + theUrl, (t) -> {
            return super.fetchStructureDefinition(theUrl);
        });
    }

    public <T extends IBaseResource> T fetchResource(@Nullable Class<T> theClass, String theUri) {
        return (T) this.loadFromCache(this.myCache, "fetchResource " + theClass + " " + theUri, (t) -> {
            return super.fetchResource(theClass, theUri);
        });
    }

    public boolean isCodeSystemSupported(ValidationSupportContext theValidationSupportContext, String theSystem) {
        String key = "isCodeSystemSupported " + theSystem;
        Boolean retVal = (Boolean)this.loadFromCacheReentrantSafe(this.myCache, key, (t) -> {
            return super.isCodeSystemSupported(theValidationSupportContext, theSystem);
        });

        assert retVal != null;

        return retVal;
    }

    public ValueSetExpansionOutcome expandValueSet(ValidationSupportContext theValidationSupportContext, ValueSetExpansionOptions theExpansionOptions, @Nonnull IBaseResource theValueSetToExpand) {
        if (!theValueSetToExpand.getIdElement().hasIdPart()) {
            return super.expandValueSet(theValidationSupportContext, theExpansionOptions, theValueSetToExpand);
        } else {
            ValueSetExpansionOptions expansionOptions = (ValueSetExpansionOptions) ObjectUtils.defaultIfNull(theExpansionOptions, EMPTY_EXPANSION_OPTIONS);
            String var10000 = theValueSetToExpand.getIdElement().getValue();
            String key = "expandValueSet " + var10000 + " " + expansionOptions.isIncludeHierarchy() + " " + expansionOptions.getFilter() + " " + expansionOptions.getOffset() + " " + expansionOptions.getCount();
            return (ValueSetExpansionOutcome)this.loadFromCache(this.myExpandValueSetCache, key, (t) -> {
                return super.expandValueSet(theValidationSupportContext, theExpansionOptions, theValueSetToExpand);
            });
        }
    }

    public CodeValidationResult validateCode(@Nonnull ValidationSupportContext theValidationSupportContext, @Nonnull ConceptValidationOptions theOptions, String theCodeSystem, String theCode, String theDisplay, String theValueSetUrl) {
        String key = "validateCode " + theCodeSystem + " " + theCode + " " + (String) StringUtils.defaultIfBlank(theValueSetUrl, "NO_VS")+ " " + StringUtils.defaultString(theDisplay, "(null)");
        //if (theDisplay != null)  return super.validateCode(theValidationSupportContext, theOptions, theCodeSystem, theCode, theDisplay, theValueSetUrl);
        return (CodeValidationResult)this.loadFromCache(this.myValidateCodeCache, key, (t) -> {
            return super.validateCode(theValidationSupportContext, theOptions, theCodeSystem, theCode, theDisplay, theValueSetUrl);
        });
    }

    public LookupCodeResult lookupCode(ValidationSupportContext theValidationSupportContext, String theSystem, String theCode, String theDisplayLanguage) {
        String key = "lookupCode " + theSystem + " " + theCode + " " + (String)StringUtils.defaultIfBlank(theDisplayLanguage, "NO_LANG");
        return (LookupCodeResult)this.loadFromCache(this.myLookupCodeCache, key, (t) -> {
            return super.lookupCode(theValidationSupportContext, theSystem, theCode, theDisplayLanguage);
        });
    }

    public LookupCodeResult lookupCode(ValidationSupportContext theValidationSupportContext, String theSystem, String theCode) {
        return this.lookupCode(theValidationSupportContext,theSystem,theCode,null);
    }


    public CodeValidationResult validateCodeInValueSet(ValidationSupportContext theValidationSupportContext, ConceptValidationOptions theValidationOptions, String theCodeSystem, String theCode, String theDisplay, @Nonnull IBaseResource theValueSet) {
        BaseRuntimeChildDefinition urlChild = this.myCtx.getResourceDefinition(theValueSet).getChildByName("url");
        Optional<String> valueSetUrl = urlChild.getAccessor().getValues(theValueSet).stream().map((t) -> {
            return ((IPrimitiveType)t).getValueAsString();
        }).filter((t) -> {
            return StringUtils.isNotBlank(t);
        }).findFirst();
        if (valueSetUrl.isPresent()) {
            String var10000 = theValidationOptions.toString();
            String key = "validateCodeInValueSet " + var10000 + " " + StringUtils.defaultString(theCodeSystem, "(null)") + " " + StringUtils.defaultString(theCode, "(null)") + " " + StringUtils.defaultString(theDisplay, "(null)") + " " + (String)valueSetUrl.get();
            //if (theDisplay != null) return super.validateCodeInValueSet(theValidationSupportContext, theValidationOptions, theCodeSystem, theCode, theDisplay, theValueSet);
            return (CodeValidationResult)this.loadFromCache(this.myValidateCodeCache, key, (t) -> {
                return super.validateCodeInValueSet(theValidationSupportContext, theValidationOptions, theCodeSystem, theCode, theDisplay, theValueSet);
            });
        } else {
            return super.validateCodeInValueSet(theValidationSupportContext, theValidationOptions, theCodeSystem, theCode, theDisplay, theValueSet);
        }
    }

    public TranslateConceptResults translateConcept(TranslateCodeRequest theRequest) {
        return (TranslateConceptResults)this.loadFromCache(this.myTranslateCodeCache, theRequest, (k) -> {
            return super.translateConcept(theRequest);
        });
    }

    @
            Nullable
    private <S, T> T loadFromCache(Cache<S, Object> theCache, S theKey, Function<S, T> theLoader) {
        ourLog.trace("Fetching from cache: {}", theKey);
        Function<S, Optional<T>> loaderWrapper = (key) -> {
            return Optional.ofNullable(theLoader.apply(theKey));
        };
        Optional<T> result = (Optional)theCache.get(theKey, loaderWrapper);

        assert result != null;

        return result.orElse((T) null);
    }

    @Nullable
    private <S, T> T loadFromCacheReentrantSafe(Cache<S, Object> theCache, S theKey, Function<S, T> theLoader) {
        ourLog.trace("Reentrant fetch from cache: {}", theKey);
        Optional<T> result = (Optional)theCache.getIfPresent(theKey);
        if (result != null && result.isPresent()) {
            return result.get();
        } else {
            T value = theLoader.apply(theKey);

            assert value != null;

            theCache.put(theKey, Optional.of(value));
            return value;
        }
    }

    private <S, T> T loadFromCacheWithAsyncRefresh(Cache<S, Object> theCache, S theKey, Function<S, T> theLoader) {
        T retVal = (T) theCache.getIfPresent(theKey);
        if (retVal == null) {
            retVal = (T) this.myNonExpiringCache.get(theKey);
            if (retVal != null) {
                Runnable loaderTask = () -> {
                    T loadedItem = this.loadFromCache(theCache, theKey, theLoader);
                    this.myNonExpiringCache.put(theKey, loadedItem);
                };
                this.myBackgroundExecutor.execute(loaderTask);
                return retVal;
            }
        }

        retVal = this.loadFromCache(theCache, theKey, theLoader);
        this.myNonExpiringCache.put(theKey, retVal);
        return retVal;
    }

    public void invalidateCaches() {
        this.myExpandValueSetCache.invalidateAll();
        this.myLookupCodeCache.invalidateAll();
        this.myCache.invalidateAll();
        this.myValidateCodeCache.invalidateAll();
        this.myNonExpiringCache.clear();
    }

    public static class CacheTimeouts {
        private long myTranslateCodeMillis;
        private long myLookupCodeMillis;
        private long myValidateCodeMillis;
        private long myMiscMillis;
        private long myExpandValueSetMillis;

        public CacheTimeouts() {
        }

        public long getExpandValueSetMillis() {
            return this.myExpandValueSetMillis;
        }

        public CacheTimeouts setExpandValueSetMillis(long theExpandValueSetMillis) {
            this.myExpandValueSetMillis = theExpandValueSetMillis;
            return this;
        }

        public long getTranslateCodeMillis() {
            return this.myTranslateCodeMillis;
        }

        public CacheTimeouts setTranslateCodeMillis(long theTranslateCodeMillis) {
            this.myTranslateCodeMillis = theTranslateCodeMillis;
            return this;
        }

        public long getLookupCodeMillis() {
            return this.myLookupCodeMillis;
        }

        public CacheTimeouts setLookupCodeMillis(long theLookupCodeMillis) {
            this.myLookupCodeMillis = theLookupCodeMillis;
            return this;
        }

        public long getValidateCodeMillis() {
            return this.myValidateCodeMillis;
        }

        public CacheTimeouts setValidateCodeMillis(long theValidateCodeMillis) {
            this.myValidateCodeMillis = theValidateCodeMillis;
            return this;
        }

        public long getMiscMillis() {
            return this.myMiscMillis;
        }

        public CacheTimeouts setMiscMillis(long theMiscMillis) {
            this.myMiscMillis = theMiscMillis;
            return this;
        }

        public static CachingValidationSupport.CacheTimeouts defaultValues() {
            return (new CachingValidationSupport.CacheTimeouts()).setLookupCodeMillis(600000L).setExpandValueSetMillis(60000L).setTranslateCodeMillis(600000L).setValidateCodeMillis(600000L).setMiscMillis(600000L);
        }
    }
}
