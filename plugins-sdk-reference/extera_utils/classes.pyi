from _typeshed import Incomplete

JavaClass: Incomplete

class _MethodMetadata:
    kind: Incomplete
    return_type: Incomplete
    arg_types: Incomplete
    modifiers: Incomplete
    throws: Incomplete
    java_name: Incomplete
    implementation: Incomplete
    code: Incomplete
    arg_names: Incomplete
    def __init__(self, kind, return_type, arg_types, modifiers, throws=None, java_name=None, implementation: str = 'python', code=None, arg_names=None) -> None: ...
    def resolve(self, fn, python_name): ...

class _ResolvedMethodMetadata:
    kind: Incomplete
    python_name: Incomplete
    java_name: Incomplete
    return_type: Incomplete
    arg_types: Incomplete
    modifiers: Incomplete
    throws: Incomplete
    implementation: Incomplete
    code: Incomplete
    arg_names: Incomplete
    signature: Incomplete
    def __init__(self, *, kind, python_name, java_name, return_type, arg_types, modifiers, throws=None, implementation: str = 'python', code=None, arg_names=None) -> None: ...

class _ConstructorMetadata:
    arg_types: Incomplete
    phase: Incomplete
    def __init__(self, arg_types=None, *, phase: str = 'post') -> None: ...
    def resolve(self, fn, python_name): ...

class _ResolvedConstructorMetadata:
    python_name: Incomplete
    arg_types: Incomplete
    phase: Incomplete
    signature: Incomplete
    def __init__(self, *, python_name, arg_types, phase) -> None: ...

class _ClassBuildMetadata:
    def resolve(self, python_name): ...

class _ResolvedClassBuildMetadata:
    python_name: Incomplete
    def __init__(self, *, python_name) -> None: ...

class _FieldMetadata:
    field_type: Incomplete
    default: Incomplete
    modifiers: Incomplete
    methods: Incomplete
    def __init__(self, field_type, default=..., modifiers: str = 'public', methods=None) -> None: ...
    def resolve(self, python_name): ...

class _ResolvedFieldMetadata:
    python_name: Incomplete
    field_type: Incomplete
    default: Incomplete
    modifiers: Incomplete
    methods: Incomplete
    def __init__(self, *, python_name, field_type, default, modifiers, methods) -> None: ...

class _FieldAccessorMetadata:
    kind: Incomplete
    name: Incomplete
    modifiers: Incomplete
    def __init__(self, kind, name=None, modifiers: str = 'public') -> None: ...
    def resolve(self, field_name, field_type): ...

class _ResolvedFieldAccessorMetadata:
    kind: Incomplete
    name: Incomplete
    modifiers: Incomplete
    def __init__(self, *, kind, name, modifiers) -> None: ...

class JField:
    __jfield__: Incomplete
    name: Incomplete
    storage_name: Incomplete
    def __init__(self, field_type, *, default=..., modifiers: str = 'public', methods=None) -> None: ...
    def __set_name__(self, owner, name) -> None: ...
    def __get__(self, instance, owner=None): ...
    def __set__(self, instance, value) -> None: ...

class JMvelMethod:
    __jproxy_method__: Incomplete
    python_name: Incomplete
    def __init__(self, metadata) -> None: ...
    def __set_name__(self, owner, name) -> None: ...
    def __get__(self, instance, owner=None): ...

class _ManagedClassBuildHook(Incomplete):
    pyclass: Incomplete
    def __init__(self, pyclass) -> None: ...
    def apply(self, dex_maker, proxy_type, super_type, interfaces) -> None: ...

class ClassInterceptor(Incomplete):
    fn: Incomplete
    pyclass: Incomplete
    kwargs: Incomplete
    def __init__(self, fn=None, pyclass=None, **kwargs) -> None: ...
    def run(self, instance, method_name, args): ...

class _ManagedClassInterceptor(Incomplete):
    pyclass: Incomplete
    instances: Incomplete
    pending_init_args: Incomplete
    def __init__(self, pyclass) -> None: ...
    def run(self, instance, method_name, args): ...
    def get_peer(self, instance): ...
    def attach_peer(self, instance, *, raw_name=None, args=None): ...
    def detach_peer(self, instance, peer): ...
    def push_pending_init_args(self, init_args): ...
    def cancel_pending_init_args(self, token) -> None: ...

def class_proxy(clazz, *, fn: callable | None = None, interceptor=None, methods=None, constructors=None, custom_name=None, **kwargs): ...

class JHelper:
    @staticmethod
    def Override(*signature, arg_types=None, modifiers: str = 'public', throws=None, name=None): ...
    @staticmethod
    def Overload(name, arg_types, *, modifiers: str = 'public', throws=None): ...
    @staticmethod
    def Method(*signature, return_type=None, arg_types=None, modifiers: str = 'public', throws=None, name=None): ...
    @staticmethod
    def MVELMethod(*, code, return_type=None, arguments=None, arg_types=None, modifiers: str = 'public', throws=None, name=None, override: bool = False): ...
    @staticmethod
    def MVELOverride(*signature, code, arguments=None, arg_types=None, modifiers: str = 'public', throws=None, name=None): ...
    @staticmethod
    def Constructor(*signature, arg_types=None): ...
    @staticmethod
    def PreConstructor(*signature, arg_types=None): ...
    @staticmethod
    def Field(field_type, *, default=..., modifiers: str = 'public', methods=None): ...
    @staticmethod
    def GetMethod(name=None, *, modifiers: str = 'public'): ...
    @staticmethod
    def SetMethod(name=None, *, modifiers: str = 'public'): ...
    @staticmethod
    def ClassBuilder(): ...

class Base:
    __jbaseclass__: Incomplete
    __jinterfaces__: Incomplete
    __jproxy_class__: Incomplete
    __jproxy_interceptor__: Incomplete
    __jproxy_custom_name__: Incomplete
    __jlegacy_methods__: Incomplete
    __jlegacy_constructors__: Incomplete
    __jsuper_bridge_base__: Incomplete
    __jresolved_methods__: Incomplete
    __jresolved_constructors__: Incomplete
    __jresolved_fields__: Incomplete
    __jmethod_dispatch__: Incomplete
    __jconstructor_dispatch__: Incomplete
    __jclass_build_hooks_cache__: Incomplete
    def __init__(self, *args, **kwargs) -> None: ...
    @classmethod
    def on_pre_init(cls, *args) -> None: ...
    def on_post_init(self, *args) -> None: ...
    @property
    def java(self): ...
    @property
    def this(self): ...
    def __getattr__(self, item): ...
    @classmethod
    def extends(cls, java_class, *interfaces, methods=None, constructors=None, custom_name=None): ...
    @classmethod
    def bind(cls, java_class, *interfaces, methods=None, constructors=None, custom_name=None): ...
    @classmethod
    def java_class(cls): ...
    @classmethod
    def from_java(cls, instance, *, init_args=None): ...
    @classmethod
    def new_java_instance(cls, *args): ...
    @classmethod
    def new_instance(cls, *args, init_args=None): ...

joverride: Incomplete
joverload: Incomplete
jmethod: Incomplete
jfield: Incomplete
jconstructor: Incomplete
jpreconstructor: Incomplete
jgetmethod: Incomplete
jsetmethod: Incomplete
jmvelmethod: Incomplete
jmveloverride: Incomplete
jMVELmethod: Incomplete
jMVELoverride: Incomplete
jclassbuilder: Incomplete
java_subclass: Incomplete

class PyObj(Base):
    obj: Incomplete
    @classmethod
    def create(cls, obj): ...
