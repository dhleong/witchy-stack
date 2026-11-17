(ns witchy.interop
  (:require [clojure.string :as str]))

(defmacro $>
  "Wraps dotted JS property access to ensure it survives :advanced
  compilation.
  
  EG:

    ($> Foo.Bar.Baz)

  Becomes:

    (.-Baz ^js (.-Bar ^js Foo))

  This is mostly for slightly cleaner-looking code, purely subjective.
  Compare:

    [:> ($> Stack.Screen.BackButton) ...]

  vs:

    [:> (j/get-in Stack [:Screen :BackButton]) ...]"
  [sym]
  (let [[root & props] (str/split (name sym) #"\.")
        root (symbol (namespace sym) root)]
    (reduce (fn [form prop]
              (list (symbol (str ".-" prop))
                    (vary-meta form assoc :tag 'js)))
            root
            props)))
