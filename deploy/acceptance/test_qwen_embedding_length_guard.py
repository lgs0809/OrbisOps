"""Protocol boundary tests; fake token counts are not model-quality evidence."""
import types
import unittest
from fastapi import FastAPI, HTTPException
from qwen_embedding_length_guard import inspect_inputs, inspect_encoder_inputs, install

class GuardTest(unittest.TestCase):
    def setUp(self):
        self.calls=[]
        def preprocess(inputs, **kwargs):
            self.calls.append(kwargs)
            return {'input_ids':types.SimpleNamespace(shape=(1,len(inputs[0])+3))}
        self.module=types.SimpleNamespace(max_seq_length=10,preprocess=preprocess,tokenizer=object())
        self.model=types.SimpleNamespace(_first_module=lambda:self.module,prompts={'default':'default','query':'query'},default_prompt_name='default')
    def test_full_input_uses_native_processor_and_prompt(self):
        result=inspect_inputs(self.model,['abc','a'*8])
        self.assertEqual([6,11],result['tokenCounts']);self.assertFalse(result['fits'])
        self.assertEqual('default',result['promptName'])
        self.assertFalse(self.calls[0]['processing_kwargs']['text']['truncation'])
        self.assertFalse(self.calls[0]['processing_kwargs']['chat_template']['truncation'])
        self.assertEqual('query',inspect_inputs(self.model,['abc'],'query')['promptName'])
    def test_custom_contract_prompt_and_dictionary_are_counted_without_conversion(self):
        seen=[]
        def preprocess(items, **kwargs):
            seen.append((items,kwargs['prompt']))
            return {'input_ids':types.SimpleNamespace(shape=(1,11))}
        self.module.preprocess=preprocess
        inputs=[{'text':'数据库故障'}]
        result=inspect_encoder_inputs(self.model,inputs,'explicit Orbis instruction')
        self.assertFalse(result['fits'])
        self.assertEqual((inputs,'explicit Orbis instruction'),seen[0])

    def test_length_error_happens_before_encoder(self):
        encoded=[]
        source=types.SimpleNamespace(app=FastAPI(),embedding_model=lambda:self.model,
            encode_items=lambda *args:encoded.append(args))
        install(source)
        with self.assertRaises(HTTPException) as error:source.encode_items(['a'*8])
        self.assertEqual(413,error.exception.status_code);self.assertEqual([],encoded)
        source.encode_items(['a'*7],'document',1024)
        self.assertEqual(1024,encoded[0][2])
    def test_busy_model_does_not_queue_or_enter_second_inference(self):
        from qwen_inference_admission import GATE, infer
        GATE.acquire()
        try:
            with self.assertRaises(HTTPException) as error: infer(lambda: self.fail('Must not run'))
            self.assertEqual(503,error.exception.status_code)
        finally: GATE.release()
        self.assertEqual(7,infer(lambda:7))

    def test_busy_guard_never_loads_a_model(self):
        from qwen_inference_admission import GATE
        source=types.SimpleNamespace(app=FastAPI(),
            embedding_model=lambda:self.fail('Busy inference must not load weights'),
            encode_items=lambda *args:self.fail('Busy inference must not encode'))
        install(source)
        GATE.acquire()
        try:
            with self.assertRaises(HTTPException) as error: source.encode_items(['short'])
            self.assertEqual(503,error.exception.status_code)
        finally: GATE.release()

    def test_invalid_and_unknown_limits_fail_closed(self):
        for texts in [[],[None],['x']*65]:
            with self.assertRaises(HTTPException):inspect_inputs(self.model,texts)
        self.module.max_seq_length=None
        with self.assertRaises(HTTPException):inspect_inputs(self.model,['abc'])

if __name__=='__main__':unittest.main()
