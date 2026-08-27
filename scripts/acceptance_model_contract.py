"""Current user-authorized roles and immutable historical receipt identities.

Historical models are admitted only when inspecting an actual saved receipt whose
requested and returned identities match. New requests use only CURRENT_*.
"""
CURRENT_SMALL_MODEL = 'gpt-6-luna'
CURRENT_LARGE_MODEL = 'gpt-6.1-terra'
CURRENT_MODELS = (CURRENT_SMALL_MODEL, CURRENT_LARGE_MODEL)
HISTORICAL_MODELS = ('gpt-5.6-luna', 'gpt-5.6-terra')
APPROVED_TRACE_MODELS = (*HISTORICAL_MODELS, *CURRENT_MODELS)
APPROVED_AUTHORING_MODELS = ('gpt-5.6-terra', CURRENT_LARGE_MODEL)
