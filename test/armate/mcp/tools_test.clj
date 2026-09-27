(ns armate.mcp.tools-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as s]
            [armate.archimate.multi-graph :as mg]
            [armate.mcp.registry :as reg]
            [armate.mcp.tools :as tools]))

(def demo-path "test/resources/demo.archimate")

(defn- with-demo
  "Run @f with a registry that has a loaded demo model, binding the rec (registry) and id."
  [f]
  (let [[r _id] (tools/handle-tool "load_model" {} {:path demo-path})]
    (f r "demo")))

(defn- txt [rc] (:text (first (:content rc))))
(defn- ok? [rc] (not (:isError rc)))

(def ^:private EXP-RENDER-SEMYA
  "@startuml \"Семья\"

!include <archimate/Archimate>

skinparam folder<<grouping>> {
  Shadowing false
}

Junction_Or(jc30, \"Причина поставить выполнение уроков на паузу\")

Business_Actor(ba11, \"Дедушка\")
Business_Actor(ba28, \"Папа\")
Business_Actor(ba35, \"Кощей\")
Business_Actor(ba36, \"Петя\")
Business_Actor(ba4, \"Мама\")
Business_Collaboration(bcb2, \"Семья Пети\")
Business_Interaction(bin32, \"Обедают\")
Business_Interaction(bin37, \"Ходит на работу\")
Business_Interaction(bin8, \"Развивает науку\")
Business_Object(bo12, \"Рыба\")
Business_Object(bo16, \"Суп\")
Business_Process(bpc17, \"Варит суп\")
Business_Process(bpc21, \"Делает уроки\")
Business_Process(bpc29, \"Ходит в магазин\")
Business_Process(bpc39, \"Делает паузу\")
Business_Process(bpc5, \"Учится в школе\")
Grouping(g20, \"Досуг дедушки\") {
  Business_Interaction(bin23, \"Играет в шахматы\")
  Business_Interaction(bin41, \"Катается на роликах\")
  Business_Process(bpc25, \"Ловит рыбу\")
}
Other_Location(l15, \"Школа\")
Other_Location(l27, \"Пространство\")
Other_Location(l38, \"Магазин\")

Rel_Specialization(bin8, bin37)
Rel_Specialization(l15, l27)
Rel_Specialization(l38, l27, \"(offview)\")
Rel_Composition(bpc5, bpc21)
Rel_Aggregation(bcb2, ba11)
Rel_Aggregation(bcb2, ba28)
Rel_Aggregation(bcb2, ba36)
Rel_Aggregation(bcb2, ba4)
Rel_Aggregation(bpc21, bpc39)
Rel_Assignment(ba11, bin23, \"(offview)\")
Rel_Assignment(ba11, bpc29)
Rel_Assignment(ba11, g20)
Rel_Assignment(ba28, bin8)
Rel_Assignment(ba28, bpc29)
Rel_Assignment(ba35, bin41)
Rel_Assignment(ba36, bin37)
Rel_Assignment(ba36, bpc5)
Rel_Assignment(ba4, bpc17)
Rel_Assignment(bcb2, bin32)
Rel_Access_w(bpc17, bo16)
Rel_Access_w(bpc25, bo12)
Rel_Access_r(bin32, bo16)
Rel_Access_r(bpc17, bo12)
Rel_Association(bpc29, l38)
Rel_Association(bpc5, l15)
Rel_Flow(bpc17, bin32)
Rel_Flow(bpc25, bpc17)
Rel_Triggering(bin32, jc30)
Rel_Triggering(bin37, jc30)
Rel_Triggering(jc30, bpc39)

@enduml")

(def ^:private EXP-RENDER-PROC-NONE
  "@startuml \"Процесс\"

!include <archimate/Archimate>

Strategy_Resource(sr9, \"Деньги\")
Business_Actor(ba1, \"Продавец пойла\")
Business_Actor(ba13, \"Волк\")
Business_Actor(ba36, \"Петя\")
Business_Actor(ba40, \"Производитель пойла\")
Business_Function(bfn31, \"Телепортирует\")
Business_Interaction(bin18, \"Оплачивает партию\")
Business_Interaction(bin37, \"Ходит на работу\")
Business_Object(bo3, \"Пойло\")
Business_Object(bo7, \"Наличные\")
Business_Process(bpc10, \"Варит пойло\")
Business_Process(bpc14, \"Отгружает на склад\")
Business_Process(bpc22, \"Получает зарплату\")
Business_Process(bpc24, \"Бухает\")
Business_Process(bpc26, \"Забирает со склада\")
Business_Process(bpc33, \"Покупает пойло\")
Business_Process(bpc34, \"Продаёт пойло\")
Other_Location(l27, \"Пространство\")
Other_Location(l38, \"Магазин\")

Rel_Specialization(l38, l27)
Rel_Composition(bpc26, bin18)
Rel_Aggregation(bin37, bfn31)
Rel_Assignment(ba1, bpc26)
Rel_Assignment(ba1, bpc34)
Rel_Assignment(ba13, bfn31)
Rel_Assignment(ba13, bin37)
Rel_Assignment(ba13, bpc22)
Rel_Assignment(ba13, bpc24)
Rel_Assignment(ba13, bpc33)
Rel_Assignment(ba36, bin37)
Rel_Assignment(ba40, bin18)
Rel_Assignment(ba40, bpc10)
Rel_Assignment(ba40, bpc14)
Rel_Realization(bo7, sr9)
Rel_Access_w(bpc10, bo3)
Rel_Access_w(bpc22, bo7)
Rel_Access_w(bpc33, bo3)
Rel_Access_w(bpc34, bo3)
Rel_Access_r(bpc14, bo3)
Rel_Access_r(bpc24, bo3)
Rel_Access_r(bpc33, bo7)
Rel_Access_r(bpc34, bo7)
Rel_Association(bfn31, l27)
Rel_Association(bin18, sr9)
Rel_Association(bpc33, l38)
Rel_Association(bpc34, l38)
Rel_Flow(bpc10, bpc14)
Rel_Flow(bpc14, bpc26)
Rel_Flow(bpc22, bpc33)
Rel_Flow(bpc26, bpc34)
Rel_Flow(bpc33, bpc24)
Rel_Triggering(bin37, bpc22)
Rel_Triggering(bpc34, bpc33)

@enduml")

(def ^:private EXP-RENDER-PROC-CERTAIN
  "@startuml \"Процесс\"

!include <archimate/Archimate>

Strategy_Resource(sr9, \"Деньги\")
Business_Actor(ba1, \"Продавец пойла\")
Business_Actor(ba13, \"Волк\")
Business_Actor(ba36, \"Петя\")
Business_Actor(ba40, \"Производитель пойла\")
Business_Function(bfn31, \"Телепортирует\")
Business_Interaction(bin18, \"Оплачивает партию\")
Business_Interaction(bin37, \"Ходит на работу\")
Business_Object(bo3, \"Пойло\")
Business_Object(bo7, \"Наличные\")
Business_Process(bpc10, \"Варит пойло\")
Business_Process(bpc14, \"Отгружает на склад\")
Business_Process(bpc22, \"Получает зарплату\")
Business_Process(bpc24, \"Бухает\")
Business_Process(bpc26, \"Забирает со склада\")
Business_Process(bpc33, \"Покупает пойло\")
Business_Process(bpc34, \"Продаёт пойло\")
Other_Location(l27, \"Пространство\")
Other_Location(l38, \"Магазин\")

Rel_Specialization(l38, l27)
Rel_Composition(bpc26, bin18)
Rel_Aggregation(bin37, bfn31)
Rel_Assignment(ba1, bin18, \"(derived-certain)\")
Rel_Assignment(ba1, bpc26)
Rel_Assignment(ba1, bpc34)
Rel_Assignment(ba13, bfn31, \"(derived-certain)\")
Rel_Assignment(ba13, bfn31, \"(derived-certain)\")
Rel_Assignment(ba13, bin37)
Rel_Assignment(ba13, bpc22)
Rel_Assignment(ba13, bpc24)
Rel_Assignment(ba13, bpc33)
Rel_Assignment(ba36, bfn31, \"(derived-certain)\")
Rel_Assignment(ba36, bin37)
Rel_Assignment(ba40, bin18)
Rel_Assignment(ba40, bpc10)
Rel_Assignment(ba40, bpc14)
Rel_Realization(bo7, sr9)
Rel_Access_w(ba1, bo3, \"(derived-certain)\")
Rel_Access_w(ba13, bo3, \"(derived-certain)\")
Rel_Access_w(ba13, bo7, \"(derived-certain)\")
Rel_Access_w(ba40, bo3, \"(derived-certain)\")
Rel_Access_w(bpc10, bo3)
Rel_Access_w(bpc22, bo7)
Rel_Access_w(bpc33, bo3)
Rel_Access_w(bpc34, bo3)
Rel_Access_r(ba1, bo7, \"(derived-certain)\")
Rel_Access_r(ba13, bo3, \"(derived-certain)\")
Rel_Access_r(ba13, bo7, \"(derived-certain)\")
Rel_Access_r(ba40, bo3, \"(derived-certain)\")
Rel_Access_r(bpc14, bo3)
Rel_Access_r(bpc24, bo3)
Rel_Access_r(bpc33, bo7)
Rel_Access_r(bpc34, bo7)
Rel_Association(ba1, bo7, \"(derived-certain)\")
Rel_Association(ba1, l38, \"(derived-certain)\")
Rel_Association(ba1, sr9, \"(derived-certain)\")
Rel_Association(ba13, l27, \"(derived-certain)\")
Rel_Association(ba13, l38, \"(derived-certain)\")
Rel_Association(ba36, l27, \"(derived-certain)\")
Rel_Association(ba40, bo7, \"(derived-certain)\")
Rel_Association(ba40, sr9, \"(derived-certain)\")
Rel_Association(bfn31, l27)
Rel_Association(bin18, bo7, \"(derived-certain)\")
Rel_Association(bin18, sr9)
Rel_Association(bin37, l27, \"(derived-certain)\")
Rel_Association(bpc26, bo7, \"(derived-certain)\")
Rel_Association(bpc26, sr9, \"(derived-certain)\")
Rel_Association(bpc33, l38)
Rel_Association(bpc34, l38)
Rel_Flow(ba1, bpc34, \"(derived-certain)\")
Rel_Flow(ba13, bpc24, \"(derived-certain)\")
Rel_Flow(ba13, bpc33, \"(derived-certain)\")
Rel_Flow(ba40, ba1, \"(derived-certain)\")
Rel_Flow(ba40, bpc14, \"(derived-certain)\")
Rel_Flow(ba40, bpc26, \"(derived-certain)\")
Rel_Flow(bpc10, ba40, \"(derived-certain)\")
Rel_Flow(bpc10, bpc14)
Rel_Flow(bpc14, ba1, \"(derived-certain)\")
Rel_Flow(bpc14, bpc26)
Rel_Flow(bpc22, ba13, \"(derived-certain)\")
Rel_Flow(bpc22, bpc33)
Rel_Flow(bpc26, ba1, \"(derived-certain)\")
Rel_Flow(bpc26, bpc34)
Rel_Flow(bpc33, ba13, \"(derived-certain)\")
Rel_Flow(bpc33, bpc24)
Rel_Triggering(ba1, bpc33, \"(derived-certain)\")
Rel_Triggering(ba13, bpc22, \"(derived-certain)\")
Rel_Triggering(ba36, bpc22, \"(derived-certain)\")
Rel_Triggering(bin37, bpc22)
Rel_Triggering(bpc34, bpc33)

@enduml")

(def ^:private EXP-RENDER-PROC-POTENTIAL
  "@startuml \"Процесс\"

!include <archimate/Archimate>

Strategy_Resource(sr9, \"Деньги\")
Business_Actor(ba1, \"Продавец пойла\")
Business_Actor(ba13, \"Волк\")
Business_Actor(ba36, \"Петя\")
Business_Actor(ba40, \"Производитель пойла\")
Business_Function(bfn31, \"Телепортирует\")
Business_Interaction(bin18, \"Оплачивает партию\")
Business_Interaction(bin37, \"Ходит на работу\")
Business_Object(bo3, \"Пойло\")
Business_Object(bo7, \"Наличные\")
Business_Process(bpc10, \"Варит пойло\")
Business_Process(bpc14, \"Отгружает на склад\")
Business_Process(bpc22, \"Получает зарплату\")
Business_Process(bpc24, \"Бухает\")
Business_Process(bpc26, \"Забирает со склада\")
Business_Process(bpc33, \"Покупает пойло\")
Business_Process(bpc34, \"Продаёт пойло\")
Other_Location(l27, \"Пространство\")
Other_Location(l38, \"Магазин\")

Rel_Specialization(l38, l27)
Rel_Composition(bpc26, bin18)
Rel_Aggregation(bin37, bfn31)
Rel_Assignment(ba1, bin18, \"(derived-certain)\")
Rel_Assignment(ba1, bpc26)
Rel_Assignment(ba1, bpc34)
Rel_Assignment(ba13, bfn31, \"(derived-certain)\")
Rel_Assignment(ba13, bfn31, \"(derived-certain)\")
Rel_Assignment(ba13, bin37)
Rel_Assignment(ba13, bpc22)
Rel_Assignment(ba13, bpc24)
Rel_Assignment(ba13, bpc33)
Rel_Assignment(ba36, bfn31, \"(derived-certain)\")
Rel_Assignment(ba36, bin37)
Rel_Assignment(ba40, bin18)
Rel_Assignment(ba40, bpc10)
Rel_Assignment(ba40, bpc14)
Rel_Realization(bo7, sr9)
Rel_Access_w(ba1, bo3, \"(derived-certain)\")
Rel_Access_w(ba13, bo3, \"(derived-certain)\")
Rel_Access_w(ba13, bo7, \"(derived-certain)\")
Rel_Access_w(ba40, bo3, \"(derived-certain)\")
Rel_Access_w(bfn31, bo3, \"(derived-potential)\")
Rel_Access_w(bfn31, bo7, \"(derived-potential)\")
Rel_Access_w(bin18, bo3, \"(derived-potential)\")
Rel_Access_w(bin37, bo3, \"(derived-potential)\")
Rel_Access_w(bin37, bo7, \"(derived-potential)\")
Rel_Access_w(bpc10, bo3, \"(derived-potential)\")
Rel_Access_w(bpc10, bo3, \"(derived-potential)\")
Rel_Access_w(bpc14, bo3, \"(derived-potential)\")
Rel_Access_w(bpc22, bo3, \"(derived-potential)\")
Rel_Access_w(bpc22, bo7, \"(derived-potential)\")
Rel_Access_w(bpc22, bo7, \"(derived-potential)\")
Rel_Access_w(bpc24, bo3, \"(derived-potential)\")
Rel_Access_w(bpc24, bo7, \"(derived-potential)\")
Rel_Access_w(bpc26, bo3, \"(derived-potential)\")
Rel_Access_w(bpc33, bo3, \"(derived-potential)\")
Rel_Access_w(bpc33, bo3, \"(derived-potential)\")
Rel_Access_w(bpc33, bo7, \"(derived-potential)\")
Rel_Access_w(bpc34, bo3, \"(derived-potential)\")
Rel_Access_w(bpc34, bo3, \"(derived-potential)\")
Rel_Access_r(ba1, bo7, \"(derived-certain)\")
Rel_Access_r(ba13, bo3, \"(derived-certain)\")
Rel_Access_r(ba13, bo7, \"(derived-certain)\")
Rel_Access_r(ba40, bo3, \"(derived-certain)\")
Rel_Access_r(bfn31, bo3, \"(derived-potential)\")
Rel_Access_r(bfn31, bo7, \"(derived-potential)\")
Rel_Access_r(bin18, bo3, \"(derived-potential)\")
Rel_Access_r(bin18, bo7, \"(derived-potential)\")
Rel_Access_r(bin37, bo3, \"(derived-potential)\")
Rel_Access_r(bin37, bo7, \"(derived-potential)\")
Rel_Access_r(bpc10, bo3, \"(derived-potential)\")
Rel_Access_r(bpc14, bo3, \"(derived-potential)\")
Rel_Access_r(bpc14, bo3, \"(derived-potential)\")
Rel_Access_r(bpc22, bo3, \"(derived-potential)\")
Rel_Access_r(bpc22, bo7, \"(derived-potential)\")
Rel_Access_r(bpc24, bo3, \"(derived-potential)\")
Rel_Access_r(bpc24, bo3, \"(derived-potential)\")
Rel_Access_r(bpc24, bo7, \"(derived-potential)\")
Rel_Access_r(bpc26, bo7, \"(derived-potential)\")
Rel_Access_r(bpc33, bo3, \"(derived-potential)\")
Rel_Access_r(bpc33, bo7, \"(derived-potential)\")
Rel_Access_r(bpc33, bo7, \"(derived-potential)\")
Rel_Access_r(bpc34, bo7, \"(derived-potential)\")
Rel_Access_r(bpc34, bo7, \"(derived-potential)\")
Rel_Association(ba1, bo7, \"(derived-certain)\")
Rel_Association(ba1, l27, \"(derived-potential)\")
Rel_Association(ba1, l38, \"(derived-certain)\")
Rel_Association(ba1, l38, \"(derived-certain)\")
Rel_Association(ba1, sr9, \"(derived-potential)\")
Rel_Association(ba1, sr9, \"(derived-potential)\")
Rel_Association(ba13, l27, \"(derived-certain)\")
Rel_Association(ba13, l27, \"(derived-certain)\")
Rel_Association(ba13, l38, \"(derived-certain)\")
Rel_Association(ba13, l38, \"(derived-certain)\")
Rel_Association(ba36, l27, \"(derived-certain)\")
Rel_Association(ba36, l27, \"(derived-certain)\")
Rel_Association(ba36, l38, \"(derived-potential)\")
Rel_Association(ba40, bo7, \"(derived-certain)\")
Rel_Association(ba40, sr9, \"(derived-potential)\")
Rel_Association(ba40, sr9, \"(derived-potential)\")
Rel_Association(bfn31, l27, \"(derived-potential)\")
Rel_Association(bfn31, l27, \"(derived-potential)\")
Rel_Association(bfn31, l38, \"(derived-potential)\")
Rel_Association(bin18, bo7, \"(derived-certain)\")
Rel_Association(bin18, bo7, \"(derived-certain)\")
Rel_Association(bin18, l27, \"(derived-potential)\")
Rel_Association(bin18, l38, \"(derived-potential)\")
Rel_Association(bin18, sr9, \"(derived-potential)\")
Rel_Association(bin18, sr9, \"(derived-potential)\")
Rel_Association(bin37, l27, \"(derived-certain)\")
Rel_Association(bin37, l27, \"(derived-certain)\")
Rel_Association(bin37, l38, \"(derived-potential)\")
Rel_Association(bpc10, bo7, \"(derived-potential)\")
Rel_Association(bpc10, sr9, \"(derived-potential)\")
Rel_Association(bpc14, bo7, \"(derived-potential)\")
Rel_Association(bpc14, sr9, \"(derived-potential)\")
Rel_Association(bpc22, l27, \"(derived-potential)\")
Rel_Association(bpc22, l38, \"(derived-potential)\")
Rel_Association(bpc24, l27, \"(derived-potential)\")
Rel_Association(bpc24, l38, \"(derived-potential)\")
Rel_Association(bpc26, bo7, \"(derived-potential)\")
Rel_Association(bpc26, bo7, \"(derived-potential)\")
Rel_Association(bpc26, l27, \"(derived-potential)\")
Rel_Association(bpc26, l38, \"(derived-potential)\")
Rel_Association(bpc26, sr9, \"(derived-potential)\")
Rel_Association(bpc26, sr9, \"(derived-potential)\")
Rel_Association(bpc33, l27, \"(derived-potential)\")
Rel_Association(bpc33, l38, \"(derived-potential)\")
Rel_Association(bpc33, l38, \"(derived-potential)\")
Rel_Association(bpc34, bo7, \"(derived-potential)\")
Rel_Association(bpc34, l27, \"(derived-potential)\")
Rel_Association(bpc34, l38, \"(derived-potential)\")
Rel_Association(bpc34, l38, \"(derived-potential)\")
Rel_Association(bpc34, sr9, \"(derived-potential)\")
Rel_Flow(ba1, bpc34, \"(derived-certain)\")
Rel_Flow(ba1, bpc34, \"(derived-certain)\")
Rel_Flow(ba13, bfn31, \"(derived-potential)\")
Rel_Flow(ba13, bin37, \"(derived-potential)\")
Rel_Flow(ba13, bpc22, \"(derived-potential)\")
Rel_Flow(ba13, bpc24, \"(derived-certain)\")
Rel_Flow(ba13, bpc24, \"(derived-certain)\")
Rel_Flow(ba13, bpc33, \"(derived-certain)\")
Rel_Flow(ba13, bpc33, \"(derived-certain)\")
Rel_Flow(ba40, ba1, \"(derived-potential)\")
Rel_Flow(ba40, ba1, \"(derived-potential)\")
Rel_Flow(ba40, bin18, \"(derived-potential)\")
Rel_Flow(ba40, bpc14, \"(derived-certain)\")
Rel_Flow(ba40, bpc14, \"(derived-certain)\")
Rel_Flow(ba40, bpc26, \"(derived-certain)\")
Rel_Flow(ba40, bpc26, \"(derived-certain)\")
Rel_Flow(ba40, bpc34, \"(derived-potential)\")
Rel_Flow(bfn31, ba13, \"(derived-potential)\")
Rel_Flow(bfn31, bin37, \"(derived-potential)\")
Rel_Flow(bfn31, bpc22, \"(derived-potential)\")
Rel_Flow(bfn31, bpc24, \"(derived-potential)\")
Rel_Flow(bfn31, bpc33, \"(derived-potential)\")
Rel_Flow(bin18, ba1, \"(derived-potential)\")
Rel_Flow(bin18, bpc14, \"(derived-potential)\")
Rel_Flow(bin18, bpc26, \"(derived-potential)\")
Rel_Flow(bin18, bpc34, \"(derived-potential)\")
Rel_Flow(bin37, ba13, \"(derived-potential)\")
Rel_Flow(bin37, bfn31, \"(derived-potential)\")
Rel_Flow(bin37, bpc22, \"(derived-potential)\")
Rel_Flow(bin37, bpc24, \"(derived-potential)\")
Rel_Flow(bin37, bpc33, \"(derived-potential)\")
Rel_Flow(bpc10, ba1, \"(derived-potential)\")
Rel_Flow(bpc10, ba40, \"(derived-certain)\")
Rel_Flow(bpc10, ba40, \"(derived-certain)\")
Rel_Flow(bpc10, bin18, \"(derived-potential)\")
Rel_Flow(bpc10, bpc14, \"(derived-potential)\")
Rel_Flow(bpc10, bpc14, \"(derived-potential)\")
Rel_Flow(bpc10, bpc26, \"(derived-potential)\")
Rel_Flow(bpc10, bpc34, \"(derived-potential)\")
Rel_Flow(bpc14, ba1, \"(derived-certain)\")
Rel_Flow(bpc14, ba1, \"(derived-certain)\")
Rel_Flow(bpc14, bin18, \"(derived-potential)\")
Rel_Flow(bpc14, bpc26, \"(derived-potential)\")
Rel_Flow(bpc14, bpc26, \"(derived-potential)\")
Rel_Flow(bpc14, bpc34, \"(derived-potential)\")
Rel_Flow(bpc22, ba13, \"(derived-certain)\")
Rel_Flow(bpc22, ba13, \"(derived-certain)\")
Rel_Flow(bpc22, bfn31, \"(derived-potential)\")
Rel_Flow(bpc22, bin37, \"(derived-potential)\")
Rel_Flow(bpc22, bpc24, \"(derived-potential)\")
Rel_Flow(bpc22, bpc33, \"(derived-potential)\")
Rel_Flow(bpc22, bpc33, \"(derived-potential)\")
Rel_Flow(bpc24, ba13, \"(derived-potential)\")
Rel_Flow(bpc24, bfn31, \"(derived-potential)\")
Rel_Flow(bpc24, bin37, \"(derived-potential)\")
Rel_Flow(bpc24, bpc22, \"(derived-potential)\")
Rel_Flow(bpc24, bpc33, \"(derived-potential)\")
Rel_Flow(bpc26, ba1, \"(derived-certain)\")
Rel_Flow(bpc26, ba1, \"(derived-certain)\")
Rel_Flow(bpc26, bin18, \"(derived-potential)\")
Rel_Flow(bpc26, bpc14, \"(derived-potential)\")
Rel_Flow(bpc26, bpc34, \"(derived-potential)\")
Rel_Flow(bpc26, bpc34, \"(derived-potential)\")
Rel_Flow(bpc33, ba13, \"(derived-certain)\")
Rel_Flow(bpc33, ba13, \"(derived-certain)\")
Rel_Flow(bpc33, bfn31, \"(derived-potential)\")
Rel_Flow(bpc33, bin37, \"(derived-potential)\")
Rel_Flow(bpc33, bpc22, \"(derived-potential)\")
Rel_Flow(bpc33, bpc24, \"(derived-potential)\")
Rel_Flow(bpc33, bpc24, \"(derived-potential)\")
Rel_Triggering(ba1, ba13, \"(derived-potential)\")
Rel_Triggering(ba1, bpc33, \"(derived-certain)\")
Rel_Triggering(ba13, bpc22, \"(derived-certain)\")
Rel_Triggering(ba36, ba13, \"(derived-potential)\")
Rel_Triggering(ba36, bpc22, \"(derived-certain)\")
Rel_Triggering(bfn31, ba13, \"(derived-potential)\")
Rel_Triggering(bfn31, bpc22, \"(derived-potential)\")
Rel_Triggering(bin18, ba13, \"(derived-potential)\")
Rel_Triggering(bin18, bpc33, \"(derived-potential)\")
Rel_Triggering(bin37, ba13, \"(derived-potential)\")
Rel_Triggering(bin37, bpc22, \"(derived-potential)\")
Rel_Triggering(bin37, bpc22, \"(derived-potential)\")
Rel_Triggering(bpc22, ba13, \"(derived-potential)\")
Rel_Triggering(bpc24, ba13, \"(derived-potential)\")
Rel_Triggering(bpc24, bpc22, \"(derived-potential)\")
Rel_Triggering(bpc26, ba13, \"(derived-potential)\")
Rel_Triggering(bpc26, bpc33, \"(derived-potential)\")
Rel_Triggering(bpc33, ba13, \"(derived-potential)\")
Rel_Triggering(bpc33, bpc22, \"(derived-potential)\")
Rel_Triggering(bpc34, ba13, \"(derived-potential)\")
Rel_Triggering(bpc34, bpc33, \"(derived-potential)\")
Rel_Triggering(bpc34, bpc33, \"(derived-potential)\")

@enduml")

(def ^:private EXP-MERGE-NONE
  "@startuml \"Семья, Шахматы\"

!include <archimate/Archimate>

skinparam folder<<grouping>> {
  Shadowing false
}

Junction_Or(jc30, \"Причина поставить выполнение уроков на паузу\")

Business_Actor(ba11, \"Дедушка\")
Business_Actor(ba13, \"Волк\")
Business_Actor(ba28, \"Папа\")
Business_Actor(ba35, \"Кощей\")
Business_Actor(ba36, \"Петя\")
Business_Actor(ba4, \"Мама\")
Business_Collaboration(bcb2, \"Семья Пети\")
Business_Function(bfn6, \"Проигрывает\")
Business_Interaction(bin32, \"Обедают\")
Business_Interaction(bin37, \"Ходит на работу\")
Business_Interaction(bin8, \"Развивает науку\")
Business_Object(bo12, \"Рыба\")
Business_Object(bo16, \"Суп\")
Business_Object(bo7, \"Наличные\")
Business_Process(bpc17, \"Варит суп\")
Business_Process(bpc19, \"Выигрывает\")
Business_Process(bpc21, \"Делает уроки\")
Business_Process(bpc29, \"Ходит в магазин\")
Business_Process(bpc39, \"Делает паузу\")
Business_Process(bpc5, \"Учится в школе\")
Grouping(g20, \"Досуг дедушки\") {
  Business_Interaction(bin23, \"Играет в шахматы\")
  Business_Interaction(bin41, \"Катается на роликах\")
  Business_Process(bpc25, \"Ловит рыбу\")
}
Other_Location(l15, \"Школа\")
Other_Location(l27, \"Пространство\")
Other_Location(l38, \"Магазин\")

Rel_Specialization(bin8, bin37)
Rel_Specialization(l15, l27)
Rel_Specialization(l38, l27, \"(offview)\")
Rel_Composition(bpc5, bpc21)
Rel_Aggregation(bcb2, ba11)
Rel_Aggregation(bcb2, ba28)
Rel_Aggregation(bcb2, ba36)
Rel_Aggregation(bcb2, ba4)
Rel_Aggregation(bin23, bfn6)
Rel_Aggregation(bin23, bpc19)
Rel_Aggregation(bpc21, bpc39)
Rel_Assignment(ba11, bfn6)
Rel_Assignment(ba11, bin23)
Rel_Assignment(ba11, bpc29)
Rel_Assignment(ba11, g20)
Rel_Assignment(ba13, bin23)
Rel_Assignment(ba13, bin37, \"(offview)\")
Rel_Assignment(ba13, bpc19)
Rel_Assignment(ba28, bin8)
Rel_Assignment(ba28, bpc29)
Rel_Assignment(ba35, bin41)
Rel_Assignment(ba36, bin37)
Rel_Assignment(ba36, bpc5)
Rel_Assignment(ba4, bpc17)
Rel_Assignment(bcb2, bin32)
Rel_Access_w(bpc17, bo16)
Rel_Access_w(bpc19, bo7)
Rel_Access_w(bpc25, bo12)
Rel_Access_r(bfn6, bo7)
Rel_Access_r(bin32, bo16)
Rel_Access_r(bpc17, bo12)
Rel_Association(bpc29, l38)
Rel_Association(bpc5, l15)
Rel_Flow(bpc17, bin32)
Rel_Flow(bpc25, bpc17)
Rel_Triggering(bin32, jc30)
Rel_Triggering(bin37, jc30)
Rel_Triggering(jc30, bpc39)

@enduml")

(def ^:private EXP-MERGE-CERTAIN
  "@startuml \"Семья, Шахматы\"

!include <archimate/Archimate>

skinparam folder<<grouping>> {
  Shadowing false
}

Junction_Or(jc30, \"Причина поставить выполнение уроков на паузу\")

Business_Actor(ba11, \"Дедушка\")
Business_Actor(ba13, \"Волк\")
Business_Actor(ba28, \"Папа\")
Business_Actor(ba35, \"Кощей\")
Business_Actor(ba36, \"Петя\")
Business_Actor(ba4, \"Мама\")
Business_Collaboration(bcb2, \"Семья Пети\")
Business_Interaction(bin32, \"Обедают\")
Business_Interaction(bin37, \"Ходит на работу\")
Business_Interaction(bin8, \"Развивает науку\")
Business_Object(bo12, \"Рыба\")
Business_Object(bo16, \"Суп\")
Business_Object(bo7, \"Наличные\")
Business_Process(bpc17, \"Варит суп\")
Business_Process(bpc21, \"Делает уроки\")
Business_Process(bpc29, \"Ходит в магазин\")
Business_Process(bpc39, \"Делает паузу\")
Business_Process(bpc5, \"Учится в школе\")
Grouping(g20, \"Досуг дедушки\") {
  Business_Function(bfn6, \"Проигрывает\")
  Business_Interaction(bin23, \"Играет в шахматы\")
  Business_Interaction(bin41, \"Катается на роликах\")
  Business_Process(bpc19, \"Выигрывает\")
  Business_Process(bpc25, \"Ловит рыбу\")
}
Other_Location(l15, \"Школа\")
Other_Location(l27, \"Пространство\")
Other_Location(l38, \"Магазин\")

Rel_Specialization(bin8, bin37)
Rel_Specialization(l15, l27)
Rel_Specialization(l38, l27, \"(offview)\")
Rel_Composition(bpc5, bpc21)
Rel_Aggregation(bcb2, ba11)
Rel_Aggregation(bcb2, ba28)
Rel_Aggregation(bcb2, ba36)
Rel_Aggregation(bcb2, ba4)
Rel_Aggregation(bin23, bfn6)
Rel_Aggregation(bin23, bpc19)
Rel_Aggregation(bpc21, bpc39)
Rel_Aggregation(bpc5, bpc39, \"(derived-certain)\")
Rel_Assignment(ba11, bfn6, \"(derived-certain)\")
Rel_Assignment(ba11, bfn6, \"(derived-certain)\")
Rel_Assignment(ba11, bin23, \"(derived-certain)\")
Rel_Assignment(ba11, bin23, \"(derived-certain)\")
Rel_Assignment(ba11, bin41, \"(derived-certain)\")
Rel_Assignment(ba11, bpc19, \"(derived-certain)\")
Rel_Assignment(ba11, bpc25, \"(derived-certain)\")
Rel_Assignment(ba11, bpc29)
Rel_Assignment(ba11, g20)
Rel_Assignment(ba13, bfn6, \"(derived-certain)\")
Rel_Assignment(ba13, bin23)
Rel_Assignment(ba13, bin37, \"(offview)\")
Rel_Assignment(ba13, bpc19, \"(derived-certain)\")
Rel_Assignment(ba13, bpc19, \"(derived-certain)\")
Rel_Assignment(ba28, bin8)
Rel_Assignment(ba28, bpc29)
Rel_Assignment(ba35, bin41)
Rel_Assignment(ba36, bin37)
Rel_Assignment(ba36, bpc21, \"(derived-certain)\")
Rel_Assignment(ba36, bpc39, \"(derived-certain)\")
Rel_Assignment(ba36, bpc5)
Rel_Assignment(ba4, bpc17)
Rel_Assignment(bcb2, bfn6, \"(derived-certain)\")
Rel_Assignment(bcb2, bin23, \"(derived-certain)\")
Rel_Assignment(bcb2, bin32)
Rel_Assignment(bcb2, bin37, \"(derived-certain)\")
Rel_Assignment(bcb2, bin41, \"(derived-certain)\")
Rel_Assignment(bcb2, bin8, \"(derived-certain)\")
Rel_Assignment(bcb2, bpc17, \"(derived-certain)\")
Rel_Assignment(bcb2, bpc19, \"(derived-certain)\")
Rel_Assignment(bcb2, bpc21, \"(derived-certain)\")
Rel_Assignment(bcb2, bpc25, \"(derived-certain)\")
Rel_Assignment(bcb2, bpc29, \"(derived-certain)\")
Rel_Assignment(bcb2, bpc39, \"(derived-certain)\")
Rel_Assignment(bcb2, bpc5, \"(derived-certain)\")
Rel_Assignment(bcb2, g20, \"(derived-certain)\")
Rel_Access_w(ba11, bo12, \"(derived-certain)\")
Rel_Access_w(ba11, bo7, \"(derived-certain)\")
Rel_Access_w(ba13, bo7, \"(derived-certain)\")
Rel_Access_w(ba4, bo16, \"(derived-certain)\")
Rel_Access_w(bcb2, bo12, \"(derived-certain)\")
Rel_Access_w(bcb2, bo16, \"(derived-certain)\")
Rel_Access_w(bcb2, bo7, \"(derived-certain)\")
Rel_Access_w(bin23, bo7, \"(derived-certain)\")
Rel_Access_w(bpc17, bo16)
Rel_Access_w(bpc19, bo7)
Rel_Access_w(bpc25, bo12)
Rel_Access_w(g20, bo12, \"(derived-certain)\")
Rel_Access_w(g20, bo7, \"(derived-certain)\")
Rel_Access_r(ba11, bo7, \"(derived-certain)\")
Rel_Access_r(ba13, bo7, \"(derived-certain)\")
Rel_Access_r(ba4, bo12, \"(derived-certain)\")
Rel_Access_r(bcb2, bo12, \"(derived-certain)\")
Rel_Access_r(bcb2, bo16, \"(derived-certain)\")
Rel_Access_r(bcb2, bo7, \"(derived-certain)\")
Rel_Access_r(bfn6, bo7)
Rel_Access_r(bin23, bo7, \"(derived-certain)\")
Rel_Access_r(bin32, bo16)
Rel_Access_r(bpc17, bo12)
Rel_Access_r(g20, bo7, \"(derived-certain)\")
Rel_Association(ba11, l38, \"(derived-certain)\")
Rel_Association(ba13, l27, \"(derived-certain)\")
Rel_Association(ba13, l38, \"(derived-certain)\")
Rel_Association(ba28, l38, \"(derived-certain)\")
Rel_Association(ba36, l15, \"(derived-certain)\")
Rel_Association(ba36, l27, \"(derived-certain)\")
Rel_Association(bcb2, l15, \"(derived-certain)\")
Rel_Association(bcb2, l27, \"(derived-certain)\")
Rel_Association(bcb2, l38, \"(derived-certain)\")
Rel_Association(bin37, l27, \"(derived-certain)\")
Rel_Association(bpc29, l38)
Rel_Association(bpc5, l15)
Rel_Flow(ba11, ba4, \"(derived-certain)\")
Rel_Flow(ba11, bcb2, \"(derived-certain)\")
Rel_Flow(ba11, bpc17, \"(derived-certain)\")
Rel_Flow(ba4, bcb2, \"(derived-certain)\")
Rel_Flow(ba4, bin32, \"(derived-certain)\")
Rel_Flow(bcb2, ba4, \"(derived-certain)\")
Rel_Flow(bcb2, bin32, \"(derived-certain)\")
Rel_Flow(bcb2, bpc17, \"(derived-certain)\")
Rel_Flow(bpc17, bcb2, \"(derived-certain)\")
Rel_Flow(bpc17, bin32)
Rel_Flow(bpc25, ba4, \"(derived-certain)\")
Rel_Flow(bpc25, bcb2, \"(derived-certain)\")
Rel_Flow(bpc25, bpc17)
Rel_Flow(g20, ba4, \"(derived-certain)\")
Rel_Flow(g20, bcb2, \"(derived-certain)\")
Rel_Flow(g20, bpc17, \"(derived-certain)\")
Rel_Triggering(bin32, jc30)
Rel_Triggering(bin37, jc30)
Rel_Triggering(jc30, bpc39)

@enduml")

(def ^:private EXP-RELATED
  "@startuml \"Волк\"

!include <archimate/Archimate>

Business_Actor(ba13, \"Волк\")
Business_Function(bfn31, \"Телепортирует\")
Business_Interaction(bin23, \"Играет в шахматы\")
Business_Interaction(bin37, \"Ходит на работу\")
Business_Process(bpc19, \"Выигрывает\")
Business_Process(bpc22, \"Получает зарплату\")
Business_Process(bpc24, \"Бухает\")
Business_Process(bpc33, \"Покупает пойло\")

Rel_Aggregation(bin23, bpc19)
Rel_Aggregation(bin37, bfn31)
Rel_Assignment(ba13, bfn31)
Rel_Assignment(ba13, bin23)
Rel_Assignment(ba13, bin37)
Rel_Assignment(ba13, bpc19)
Rel_Assignment(ba13, bpc22)
Rel_Assignment(ba13, bpc24)
Rel_Assignment(ba13, bpc33)
Rel_Flow(bpc22, bpc33)
Rel_Flow(bpc33, bpc24)
Rel_Triggering(bin37, bpc22)

@enduml")

(def ^:private EXP-LIST
  "Бухает | bpc24 | business-process | business | views: Процесс
Варит пойло | bpc10 | business-process | business | views: Процесс
Варит суп | bpc17 | business-process | business | views: Семья
Волк | ba13 | business-actor | business | views: Процесс,Шахматы
Выигрывает | bpc19 | business-process | business | views: Шахматы
Дедушка | ba11 | business-actor | business | views: Семья,Шахматы
Делает паузу | bpc39 | business-process | business | views: Семья
Делает уроки | bpc21 | business-process | business | views: Семья
Деньги | sr9 | strategy-resource | strategy | views: Процесс
Досуг дедушки | g20 | grouping | grouping | views: Семья
Забирает со склада | bpc26 | business-process | business | views: Процесс
Играет в шахматы | bin23 | business-interaction | business | views: Семья,Шахматы
Катается на роликах | bin41 | business-interaction | business | views: Семья
Кощей | ba35 | business-actor | business | views: Семья
Ловит рыбу | bpc25 | business-process | business | views: Семья
Магазин | l38 | location | location | views: Процесс,Семья
Мама | ba4 | business-actor | business | views: Семья
Наличные | bo7 | business-object | business | views: Процесс,Шахматы
Обедают | bin32 | business-interaction | business | views: Семья
Оплачивает партию | bin18 | business-interaction | business | views: Процесс
Отгружает на склад | bpc14 | business-process | business | views: Процесс
Папа | ba28 | business-actor | business | views: Семья
Петя | ba36 | business-actor | business | views: Процесс,Семья
Пойло | bo3 | business-object | business | views: Процесс
Покупает пойло | bpc33 | business-process | business | views: Процесс
Получает зарплату | bpc22 | business-process | business | views: Процесс
Продавец пойла | ba1 | business-actor | business | views: Процесс
Продаёт пойло | bpc34 | business-process | business | views: Процесс
Проигрывает | bfn6 | business-function | business | views: Шахматы
Производитель пойла | ba40 | business-actor | business | views: Процесс
Пространство | l27 | location | location | views: Процесс,Семья
Развивает науку | bin8 | business-interaction | business | views: Семья
Рыба | bo12 | business-object | business | views: Семья
Семья Пети | bcb2 | business-collaboration | business | views: Семья
Суп | bo16 | business-object | business | views: Семья
Телепортирует | bfn31 | business-function | business | views: Процесс
Учится в школе | bpc5 | business-process | business | views: Семья
Ходит в магазин | bpc29 | business-process | business | views: Семья
Ходит на работу | bin37 | business-interaction | business | views: Процесс,Семья
Школа | l15 | location | location | views: Семья")

(def ^:private EXP-LIST-PROC
  "Бухает | bpc24 | business-process | business | views: Процесс
Варит пойло | bpc10 | business-process | business | views: Процесс
Волк | ba13 | business-actor | business | views: Процесс,Шахматы
Деньги | sr9 | strategy-resource | strategy | views: Процесс
Забирает со склада | bpc26 | business-process | business | views: Процесс
Магазин | l38 | location | location | views: Процесс,Семья
Наличные | bo7 | business-object | business | views: Процесс,Шахматы
Оплачивает партию | bin18 | business-interaction | business | views: Процесс
Отгружает на склад | bpc14 | business-process | business | views: Процесс
Петя | ba36 | business-actor | business | views: Процесс,Семья
Пойло | bo3 | business-object | business | views: Процесс
Покупает пойло | bpc33 | business-process | business | views: Процесс
Получает зарплату | bpc22 | business-process | business | views: Процесс
Продавец пойла | ba1 | business-actor | business | views: Процесс
Продаёт пойло | bpc34 | business-process | business | views: Процесс
Производитель пойла | ba40 | business-actor | business | views: Процесс
Пространство | l27 | location | location | views: Процесс,Семья
Телепортирует | bfn31 | business-function | business | views: Процесс
Ходит на работу | bin37 | business-interaction | business | views: Процесс,Семья")

(def ^:private EXP-LIST-VIEWS
  "Процесс
Семья
Шахматы")

(def ^:private EXP-ELEM-VIEWS
  "Процесс
Шахматы")

(def ^:private EXP-REL-VIEWS
  "assignment: {Процесс}")

(def ^:private EXP-STATS
  "{:types 45, :elements {:total 40, :groups {:subject 9, :behavior 22, nil 1, :object 4, :composite 4}, :inside {:strategy-resource 1, :business-process 14, :business-function 2, :grouping 1, :business-collaboration 1, :business-object 4, :business-actor 8, :business-interaction 6, :location 3}, :layer {:business 35, :strategy 1, :location 3, :grouping 1}}, :relations {:original {:total 72, :groups {:structural 37, :dependency 20, :dynamic 12, :other 3}, :inside {:access_r 7, :flow 7, :access_w 7, :triggering 5, :specialization 3, :association 6, :assignment 23, :aggregation 9, :composition 4, :realization 1}}, :nesting {:total 0, :groups {}, :inside {}}, :certain {:total 100, :groups {:dependency 42, :dynamic 29, :structural 29}, :inside {:association 18, :flow 25, :assignment 26, :access_w 13, :access_r 11, :triggering 4, :aggregation 3}}, :potential {:total 0, :groups {}, :inside {}}}, :lints 0}")

(def ^:private EXP-DERIVED
  "Играет в шахматы(access_w) -> Наличные
Играет в шахматы(access_r) -> Наличные
Получает зарплату(flow) -> Волк
Покупает пойло(flow) -> Волк
Волк(assignment) -> Выигрывает
Волк(flow) -> Бухает
Волк(access_r) -> Наличные
Волк(access_w) -> Наличные
Волк(triggering) -> Получает зарплату
Волк(assignment) -> Проигрывает
Волк(flow) -> Покупает пойло
Волк(assignment) -> Телепортирует")

(def ^:private EXP-DER-EMPTY
  "No derived relations between Деньги and Пойло")

(def ^:private EXP-DER-D1
  "Получает зарплату(flow) -> Волк
Покупает пойло(flow) -> Волк
Волк(assignment) -> Выигрывает
Волк(flow) -> Бухает
Волк(access_r) -> Наличные
Волк(access_w) -> Наличные
Волк(triggering) -> Получает зарплату
Волк(association) -> Магазин
Волк(flow) -> Покупает пойло
Волк(access_r) -> Пойло
Волк(access_w) -> Пойло
Волк(assignment) -> Телепортирует")

(def ^:private EXP-DER-D3
  "Продавец пойла(triggering) -> Покупает пойло
Получает зарплату(flow) -> Волк
Покупает пойло(flow) -> Волк
Волк(assignment) -> Выигрывает
Волк(flow) -> Бухает
Волк(access_r) -> Наличные
Волк(access_w) -> Наличные
Волк(association) -> Пространство
Волк(triggering) -> Получает зарплату
Волк(association) -> Магазин
Волк(assignment) -> Проигрывает
Волк(flow) -> Покупает пойло
Волк(access_r) -> Пойло
Волк(access_w) -> Пойло
Волк(assignment) -> Телепортирует")

(def ^:private EXP-SP
  "Производитель пойла -(assignment)-> Варит пойло -(access_w)-> Пойло <-(access_r)- Бухает <-(assignment)- Волк")

(def ^:private EXP-SPD
  "Волк -(assignment)-> Бухает")

(def ^:private EXP-AP
  "Производитель пойла -(assignment)-> Варит пойло -(access_w)-> Пойло <-(access_r)- Бухает <-(assignment)- Волк

Производитель пойла -(assignment)-> Варит пойло -(access_w)-> Пойло <-(access_w)- Покупает пойло <-(assignment)- Волк

Производитель пойла -(assignment)-> Отгружает на склад -(access_r)-> Пойло <-(access_r)- Бухает <-(assignment)- Волк

Производитель пойла -(assignment)-> Отгружает на склад -(access_r)-> Пойло <-(access_w)- Покупает пойло <-(assignment)- Волк

Производитель пойла -(assignment)-> Варит пойло -(access_w)-> Пойло <-(access_r)- Бухает <-(flow)- Покупает пойло <-(assignment)- Волк

Производитель пойла -(assignment)-> Варит пойло -(access_w)-> Пойло <-(access_w)- Покупает пойло -(flow)-> Бухает <-(assignment)- Волк

Производитель пойла -(assignment)-> Варит пойло -(access_w)-> Пойло <-(access_w)- Покупает пойло <-(flow)- Получает зарплату <-(assignment)- Волк

Производитель пойла -(assignment)-> Варит пойло -(access_w)-> Пойло <-(access_w)- Продаёт пойло -(triggering)-> Покупает пойло <-(assignment)- Волк

Производитель пойла -(assignment)-> Варит пойло -(flow)-> Отгружает на склад -(access_r)-> Пойло <-(access_r)- Бухает <-(assignment)- Волк

Производитель пойла -(assignment)-> Варит пойло -(flow)-> Отгружает на склад -(access_r)-> Пойло <-(access_w)- Покупает пойло <-(assignment)- Волк

Производитель пойла -(assignment)-> Оплачивает партию -(association)-> Деньги <-(realization)- Наличные <-(access_w)- Выигрывает <-(assignment)- Волк

Производитель пойла -(assignment)-> Оплачивает партию -(association)-> Деньги <-(realization)- Наличные <-(access_w)- Получает зарплату <-(assignment)- Волк

Производитель пойла -(assignment)-> Оплачивает партию -(association)-> Деньги <-(realization)- Наличные <-(access_r)- Покупает пойло <-(assignment)- Волк

Производитель пойла -(assignment)-> Оплачивает партию <-(composition)- Забирает со склада -(flow)-> Продаёт пойло -(triggering)-> Покупает пойло <-(assignment)- Волк

Производитель пойла -(assignment)-> Отгружает на склад -(flow)-> Забирает со склада -(flow)-> Продаёт пойло -(triggering)-> Покупает пойло <-(assignment)- Волк

Производитель пойла -(assignment)-> Отгружает на склад -(access_r)-> Пойло <-(access_r)- Бухает <-(flow)- Покупает пойло <-(assignment)- Волк

Производитель пойла -(assignment)-> Отгружает на склад -(access_r)-> Пойло <-(access_w)- Покупает пойло -(flow)-> Бухает <-(assignment)- Волк

Производитель пойла -(assignment)-> Отгружает на склад -(access_r)-> Пойло <-(access_w)- Покупает пойло <-(flow)- Получает зарплату <-(assignment)- Волк

Производитель пойла -(assignment)-> Отгружает на склад -(access_r)-> Пойло <-(access_w)- Продаёт пойло -(triggering)-> Покупает пойло <-(assignment)- Волк

Производитель пойла -(assignment)-> Отгружает на склад <-(flow)- Варит пойло -(access_w)-> Пойло <-(access_r)- Бухает <-(assignment)- Волк

Производитель пойла -(assignment)-> Отгружает на склад <-(flow)- Варит пойло -(access_w)-> Пойло <-(access_w)- Покупает пойло <-(assignment)- Волк")

(def ^:private EXP-APD
  "Волк -(assignment)-> Бухает

Волк -(assignment)-> Покупает пойло -(flow)-> Бухает

Волк -(assignment)-> Получает зарплату -(flow)-> Покупает пойло -(flow)-> Бухает

Волк -(assignment)-> Ходит на работу -(triggering)-> Получает зарплату -(flow)-> Покупает пойло -(flow)-> Бухает")

(def ^:private EXP-AP-WOLF-BUY
  "Волк -(assignment)-> Покупает пойло

Волк -(assignment)-> Получает зарплату -(flow)-> Покупает пойло

Волк -(assignment)-> Бухает <-(flow)- Покупает пойло

Волк -(assignment)-> Выигрывает -(access_w)-> Наличные <-(access_r)- Покупает пойло

Волк -(assignment)-> Получает зарплату -(access_w)-> Наличные <-(access_r)- Покупает пойло

Волк -(assignment)-> Бухает -(access_r)-> Пойло <-(access_w)- Покупает пойло

Волк -(assignment)-> Ходит на работу -(triggering)-> Получает зарплату -(flow)-> Покупает пойло

Волк -(assignment)-> Выигрывает -(access_w)-> Наличные <-(access_w)- Получает зарплату -(flow)-> Покупает пойло

Волк -(assignment)-> Выигрывает -(access_w)-> Наличные <-(access_r)- Продаёт пойло -(triggering)-> Покупает пойло

Волк -(assignment)-> Телепортирует -(association)-> Пространство <-(specialization)- Магазин <-(association)- Покупает пойло

Волк -(assignment)-> Телепортирует <-(aggregation)- Ходит на работу -(triggering)-> Получает зарплату -(flow)-> Покупает пойло

Волк -(assignment)-> Получает зарплату -(access_w)-> Наличные <-(access_r)- Продаёт пойло -(triggering)-> Покупает пойло

Волк -(assignment)-> Бухает -(access_r)-> Пойло <-(access_w)- Продаёт пойло -(triggering)-> Покупает пойло

Волк -(assignment)-> Ходит на работу -(triggering)-> Получает зарплату -(access_w)-> Наличные <-(access_r)- Покупает пойло

Волк -(assignment)-> Играет в шахматы -(aggregation)-> Проигрывает -(access_r)-> Наличные <-(access_r)- Покупает пойло

Волк -(assignment)-> Играет в шахматы -(aggregation)-> Выигрывает -(access_w)-> Наличные <-(access_r)- Покупает пойло

Волк -(assignment)-> Выигрывает -(access_w)-> Наличные <-(access_r)- Продаёт пойло -(association)-> Магазин <-(association)- Покупает пойло

Волк -(assignment)-> Выигрывает -(access_w)-> Наличные <-(access_r)- Продаёт пойло -(access_w)-> Пойло <-(access_w)- Покупает пойло

Волк -(assignment)-> Выигрывает <-(aggregation)- Играет в шахматы -(aggregation)-> Проигрывает -(access_r)-> Наличные <-(access_r)- Покупает пойло

Волк -(assignment)-> Телепортирует -(association)-> Пространство <-(specialization)- Магазин <-(association)- Продаёт пойло -(triggering)-> Покупает пойло

Волк -(assignment)-> Телепортирует <-(aggregation)- Ходит на работу -(triggering)-> Получает зарплату -(access_w)-> Наличные <-(access_r)- Покупает пойло

Волк -(assignment)-> Получает зарплату -(access_w)-> Наличные <-(access_r)- Продаёт пойло -(association)-> Магазин <-(association)- Покупает пойло

Волк -(assignment)-> Получает зарплату -(access_w)-> Наличные <-(access_r)- Продаёт пойло -(access_w)-> Пойло <-(access_w)- Покупает пойло

Волк -(assignment)-> Бухает -(access_r)-> Пойло <-(access_w)- Продаёт пойло -(access_r)-> Наличные <-(access_r)- Покупает пойло

Волк -(assignment)-> Бухает -(access_r)-> Пойло <-(access_w)- Продаёт пойло -(association)-> Магазин <-(association)- Покупает пойло

Волк -(assignment)-> Ходит на работу -(aggregation)-> Телепортирует -(association)-> Пространство <-(specialization)- Магазин <-(association)- Покупает пойло

Волк -(assignment)-> Ходит на работу -(triggering)-> Получает зарплату -(access_w)-> Наличные <-(access_r)- Продаёт пойло -(triggering)-> Покупает пойло

Волк -(assignment)-> Играет в шахматы -(aggregation)-> Проигрывает -(access_r)-> Наличные <-(access_w)- Получает зарплату -(flow)-> Покупает пойло

Волк -(assignment)-> Играет в шахматы -(aggregation)-> Проигрывает -(access_r)-> Наличные <-(access_r)- Продаёт пойло -(triggering)-> Покупает пойло

Волк -(assignment)-> Играет в шахматы -(aggregation)-> Выигрывает -(access_w)-> Наличные <-(access_w)- Получает зарплату -(flow)-> Покупает пойло

Волк -(assignment)-> Играет в шахматы -(aggregation)-> Выигрывает -(access_w)-> Наличные <-(access_r)- Продаёт пойло -(triggering)-> Покупает пойло

Волк -(assignment)-> Играет в шахматы <-(assignment)- Дедушка -(assignment)-> Ходит в магазин -(association)-> Магазин <-(association)- Покупает пойло

Волк -(assignment)-> Играет в шахматы <-(assignment)- Дедушка -(assignment)-> Проигрывает -(access_r)-> Наличные <-(access_r)- Покупает пойло")

(def ^:private EXP-ERR-MODEL
  "Unknown model_id: nope")

(def ^:private EXP-ERR-ELEM
  "Unknown element: Наличн
Did you mean: Наличные [bo7 business-object business]")

(def ^:private EXP-ERR-VOVK
  "Unknown element: Вовк
Did you mean: Волк [ba13 business-actor business]")

(def ^:private EXP-ERR-AMBIG
  "Ambiguous element name: Волк
Candidates: Волк [ba1], Волк [ba13]")

(deftest tool-list-18-tools
  (testing "tool-list advertises exactly the 18 armate tools"
    (let [names (sort (map :name (tools/tool-list)))]
      (is (= 18 (count names)))
      (is (= '("all_paths" "derived_relations" "element_views" "filter_by_layer"
               "filter_by_type" "get_stats" "lint_plantuml" "list_elements" "list_models"
               "list_views" "load_model" "merge_views" "related_elements" "relation_views"
               "reload_model" "render_view" "shortest_path" "unload_model")
             names)))))

(deftest tool-descriptions-document-markers
  (testing "render_view and merge_views descriptions document the marker vocabulary"
    (let [defs (tools/tool-list)
          rv (first (filter #(= "render_view" (:name %)) defs))
          mv (first (filter #(= "merge_views" (:name %)) defs))]
      (is (s/includes? (:description rv) "(offview)"))
      (is (s/includes? (:description rv) "(derived-certain)"))
      (is (s/includes? (:description mv) "(offview)"))
      (is (s/includes? (:description mv) "(derived-certain)")))))

(deftest tool-description-lint-plantuml-guides
  (testing "lint_plantuml description covers the ArchiMate-only scope and CLI guidance"
    (let [d (first (filter #(= "lint_plantuml" (:name %)) (tools/tool-list)))
          desc (:description d)]
      (is (s/includes? desc "ArchiMate"))
      (is (s/includes? desc "PlantUML CLI"))
      (is (s/includes? desc "sequence")))
    (let [d (first (filter #(= "lint_plantuml" (:name %)) (tools/tool-list)))]
      (is (= ["content"] (get-in d [:inputSchema :required])))
      (is (= "string" (get-in d [:inputSchema :properties "content" :type]))))))

(deftest load-model-returns-id
  (testing "load_model loads the demo and returns its model_id"
    (with-demo
      (fn [_r id]
        (is (= "demo" id))))))

(deftest list-views
  (testing "list_views returns the demo views"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "list_views" r {:model_id id})]
          (is (ok? rc))
          (is (= EXP-LIST-VIEWS (txt rc))))))))

(deftest list-elements
  (testing "list_elements lists the whole model in full"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "list_elements" r {:model_id id})]
          (is (ok? rc))
          (is (= EXP-LIST (txt rc))))))))

(deftest list-elements-with-view
  (testing "list_elements narrows to a view's sub-context"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "list_elements" r
                                        {:model_id id :view "Процесс"})]
          (is (ok? rc))
          (is (= EXP-LIST-PROC (txt rc))))))))

(deftest element-views-and-relation-views
  (testing "element_views / relation_views resolve fully"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "element_views" r
                                        {:model_id id :name "Волк"})]
          (is (ok? rc))
          (is (= EXP-ELEM-VIEWS (txt rc))))
        (let [[_ rc] (tools/handle-tool "relation_views" r
                                        {:model_id id :from_name "Волк" :to_name "Бухает"})]
          (is (ok? rc))
          (is (= EXP-REL-VIEWS (txt rc))))))))

(deftest render-view-no-mode
  (testing "render_view defaults to mode none and outputs the full Семья document,
            marking off-view relations and leaving placed ones unmarked"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "render_view" r
                                        {:model_id id :view "Семья"})]
          (is (ok? rc))
          (is (= EXP-RENDER-SEMYA (txt rc))))))))

(deftest render-view-mode-controls-derived-edges
  (testing "render_view renders derived edges per mode: none/certain/both as full documents"
    (with-demo
      (fn [r id]
        (let [[_ none-rc] (tools/handle-tool "render_view" r
                                             {:model_id id :view "Процесс" :mode "none"})
              [_ certain-rc] (tools/handle-tool "render_view" r
                                                {:model_id id :view "Процесс" :mode "certain"})
              [_ both-rc] (tools/handle-tool "render_view" r
                                             {:model_id id :view "Процесс" :mode "certain+potential"})]
          (is (ok? none-rc))
          (is (ok? certain-rc))
          (is (ok? both-rc))
          (is (= EXP-RENDER-PROC-NONE (txt none-rc)))
          (is (= EXP-RENDER-PROC-CERTAIN (txt certain-rc)))
          (is (= EXP-RENDER-PROC-POTENTIAL (txt both-rc))))))))

(deftest merge-views-mode-controls-derived-edges
  (testing "merge_views unions the views per mode as full documents"
    (with-demo
      (fn [r id]
        (let [[_ none-rc] (tools/handle-tool "merge_views" r
                                             {:model_id id :views ["Семья" "Шахматы"] :mode "none"})
              [_ certain-rc] (tools/handle-tool "merge_views" r
                                                {:model_id id :views ["Семья" "Шахматы"] :mode "certain"})]
          (is (ok? none-rc))
          (is (ok? certain-rc))
          (is (= EXP-MERGE-NONE (txt none-rc)))
          (is (= EXP-MERGE-CERTAIN (txt certain-rc))))))))

(deftest render-view-marks-offview-and-derived
  (testing "the marker vocabulary is observable in the full documents: (offview) for
            non-placed original relations, (derived-certain)/(derived-potential) for
            inferred ones, and no marker on genuinely placed relations"
    (let [semya EXP-RENDER-SEMYA
          proc-none EXP-RENDER-PROC-NONE
          proc-certain EXP-RENDER-PROC-CERTAIN
          proc-potential EXP-RENDER-PROC-POTENTIAL]
      (testing "offview appears only on the relation placed on no view"
        (is (s/includes? semya "Rel_Specialization(l38, l27, \"(offview)\")"))
        (is (s/includes? semya "Rel_Assignment(ba11, bin23, \"(offview)\")"))
        (is (not (s/includes? semya "Rel_Assignment(ba11, bpc29, \"(offview)\")"))
            "a placed relation (ba11 -> bpc29) carries no marker"))
      (testing "the Процесс view has no offview relations"
        (is (not (s/includes? proc-none "(offview)"))))
      (testing "derived-certain markers appear under mode certain, no offview"
        (is (s/includes? proc-certain "(derived-certain)"))
        (is (not (s/includes? proc-certain "(offview)"))))
      (testing "potential markers appear only under mode certain+potential"
        (is (s/includes? proc-potential "(derived-potential)"))
        (is (not (s/includes? proc-certain "(derived-potential)")))
        (is (not (s/includes? proc-none "(derived-potential)")))))))

(deftest render-view-does-not-mark-related-elements
  (testing "related_elements output is unmarked and equals its full document"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "related_elements" r
                                        {:model_id id :name "Волк"})]
          (is (ok? rc))
          (is (= EXP-RELATED (txt rc)))
          (is (not (s/includes? (txt rc) "(offview)"))
              "related_elements is untouched by marker logic"))))))

(deftest related-and-paths
  (testing "related_elements renders; shortest_path and all_paths render full strings"
    (with-demo
      (fn [r id]
        (let [[_ short-rc] (tools/handle-tool "shortest_path" r
                                              {:model_id id
                                               :from_name "Производитель пойла"
                                               :to_name "Волк"})
              [_ all-rc] (tools/handle-tool "all_paths" r
                                            {:model_id id
                                             :from_name "Производитель пойла"
                                             :to_name "Волк"})]
          (is (ok? short-rc))
          (is (ok? all-rc))
          (is (= EXP-SP (txt short-rc)))
          (is (= EXP-AP (txt all-rc))))))))

(deftest direct-path-uses-names-and-forward-arrows
  (testing "directed=true keeps -> everywhere and uses element names"
    (with-demo
      (fn [r id]
        (let [[_ sp-rc] (tools/handle-tool "shortest_path" r
                                           {:model_id id :from_name "Волк" :to_name "Бухает"
                                            :directed true})
              [_ ap-rc] (tools/handle-tool "all_paths" r
                                           {:model_id id :from_name "Волк" :to_name "Бухает"
                                            :directed true})]
          (is (ok? sp-rc))
          (is (ok? ap-rc))
          (is (= EXP-SPD (txt sp-rc)))
          (is (= EXP-APD (txt ap-rc))))))))

(deftest all-paths-wolf-to-buy
  (testing "undirected all_paths surfaces backward edges and matches its full path list"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "all_paths" r
                                        {:model_id id :from_name "Волк" :to_name "Покупает пойло"})]
          (is (ok? rc))
          (is (= EXP-AP-WOLF-BUY (txt rc))))))))

(deftest get-stats-tool
  (testing "get_stats reports the full stable stats document"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "get_stats" r {:model_id id})]
          (is (ok? rc))
          (is (= EXP-STATS (txt rc))))))))

(deftest get-stats-reports-derived-certain-after-load
  (testing "get_stats right after load_model reports the globally derived certain relations"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "get_stats" r {:model_id id})
              stats (read-string (txt rc))]
          (is (ok? rc))
          (is (pos? (get-in stats [:relations :certain :total])))
          (is (zero? (get-in stats [:relations :potential :total]))))))))

(deftest get-stats-certain-agrees-with-certain-graph
  (testing "get_stats :relations.certain agrees with the registry's certain derivation"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "get_stats" r {:model_id id})
              stats (read-string (txt rc))
              certain (reg/certain-graph r id)
              aliases (set (keys (:elements certain)))
              expected-count (->> (mg/get-relationships (:relations certain))
                                  (filter (fn [[from to rel]]
                                            (and (aliases from) (aliases to)
                                                 (= :certain (:derivate rel)))))
                                  (count))]
          (is (ok? rc))
          (is (= expected-count (get-in stats [:relations :certain :total]))))))))

(deftest get-stats-reload-invalidates-derived-counts
  (testing "reload_model invalidates derived counts; re-read yields equivalent counts"
    (with-demo
      (fn [r id]
        (let [[_ rc-first] (tools/handle-tool "get_stats" r {:model_id id})
              stats-first (read-string (txt rc-first))
              [r2 _] (tools/handle-tool "reload_model" r {:model_id id})
              [_ rc-second] (tools/handle-tool "get_stats" r2 {:model_id id})
              stats-second (read-string (txt rc-second))]
          (is (ok? rc-first))
          (is (map? (:relations stats-second)))
          (is (= (get-in stats-first [:relations :certain :total])
                 (get-in stats-second [:relations :certain :total]))))))))

(deftest get-stats-stable-shape
  (testing "get_stats keeps the stable stats shape: all relation buckets always present"
    (let [stats (read-string EXP-STATS)]
      (is (contains? (set (keys (:relations stats))) :certain))
      (is (every? #(map? %) (vals (:relations stats)))))))

(deftest derived-relations-found
  (testing "derived_relations lists certain-derived relations between two elements fully"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "derived_relations" r
                                        {:model_id id :from_name "Волк" :to_name "Наличные"})]
          (is (ok? rc))
          (is (= EXP-DERIVED (txt rc))))))))

(deftest derived-relations-empty
  (testing "derived_relations reports a clear message when none exist"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "derived_relations" r
                                        {:model_id id :from_name "Деньги" :to_name "Пойло"})]
          (is (ok? rc))
          (is (= EXP-DER-EMPTY (txt rc))))))))

(deftest derived-relations-depth-window
  (testing "derived_relations scopes to the depth window around each element"
    (with-demo
      (fn [r id]
        (let [[_ shallow] (tools/handle-tool "derived_relations" r
                                             {:model_id id
                                              :from_name "Волк" :to_name "Покупает пойло"
                                              :depth 1})
              [_ deep] (tools/handle-tool "derived_relations" r
                                          {:model_id id
                                           :from_name "Волк" :to_name "Покупает пойло"
                                           :depth 3})]
          (is (= EXP-DER-D1 (txt shallow)))
          (is (= EXP-DER-D3 (txt deep))))))))

(deftest derived-relations-tool-schema
  (testing "derived_relations is advertised with an inputSchema"
    (let [defs (tools/tool-list)
          rv (first (filter #(= "derived_relations" (:name %)) defs))]
      (is (some? rv))
      (is (= "object" (get-in rv [:inputSchema :type])))
      (is (every? #(contains? (get-in rv [:inputSchema :properties]) %)
                  ["model_id" "from_name" "to_name"])))))

(deftest error-unknown-model
  (testing "unknown model_id is an isError result with a full message"
    (let [[_ rc] (tools/handle-tool "list_views" {} {:model_id "nope"})]
      (is (not (ok? rc)))
      (is (= EXP-ERR-MODEL (txt rc))))))

(deftest error-unknown-element-suggests-nearest
  (testing "shortest_path with a near-miss name suggests the closest real element"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "shortest_path" r
                                        {:model_id id :from_name "Волк" :to_name "Наличн"})]
          (is (not (ok? rc)))
          (is (= EXP-ERR-ELEM (txt rc)))))))
  (testing "element_views with a near-miss name suggests the closest real element"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "element_views" r
                                        {:model_id id :name "Вовк"})]
          (is (not (ok? rc)))
          (is (= EXP-ERR-VOVK (txt rc))))))))

(deftest error-ambiguous-element-lists-candidates
  (testing "a name shared by several elements is an isError listing candidates"
    (with-demo
      (fn [r id]
        (let [r (assoc-in r [id :context :elements "ba1" :name] "Волк")
              [_ rc] (tools/handle-tool "element_views" r
                                        {:model_id id :name "Волк"})]
          (is (not (ok? rc)))
          (is (= EXP-ERR-AMBIG (txt rc))))))))

(deftest tool-list-has-input-schemas
  (testing "every advertised tool carries a non-empty inputSchema"
    (let [defs (tools/tool-list)
          rv (first (filter #(= "render_view" (:name %)) defs))
          props (get-in rv [:inputSchema :properties])]
      (is (every? #(map? (get-in % [:inputSchema :properties])) defs))
      (is (contains? props "model_id"))
      (is (contains? props "view"))
      (is (contains? props "mode"))
      (is (= "object" (get-in rv [:inputSchema :type])))
      (is (contains? (set (get-in rv [:inputSchema :required])) "view")))))

(deftest render-uses-stable-aliases
  (testing "render_view uses the same global aliases as list_elements"
    (with-demo
      (fn [r id]
        (let [[_ rc-list] (tools/handle-tool "list_elements" r {:model_id id})
              line (some #(when (s/includes? % "Волк") %) (s/split-lines (txt rc-list)))
              list-alias (second (re-find #"Волк \| ([a-z0-9]+)" line))]
          (is (some? list-alias))
          (is (= "ba13" list-alias))
          (is (s/includes? EXP-RENDER-PROC-NONE
                           (str "Business_Actor(" list-alias ", \"Волк\")"))))))))

;; ---------------------------------------------------------------------------
;; lint_plantuml
;; ---------------------------------------------------------------------------

(def ^:private LINT-VALID
  "@startuml \"Семья\"\n\n!include <archimate/Archimate>\n\nBusiness_Actor(ba4, \"Мама\")\nBusiness_Process(bpc17, \"Варит суп\")\n\nRel_Assignment(ba4, bpc17)\n\n@enduml")

(def ^:private LINT-UNSPECIFIED
  "\n@startuml\n\n!$app = \"jar:archimate/application\"\n!include <archimate/Archimate>\n\nsprite $aComponent $app-component\n\nrectangle \"Component1\" as c1 <<$aComponent>>\nrectangle \"Component 2\" as c2 <<$aComponent>>\n\nc1 ~ c2\n\n@enduml")

(def ^:private LINT-UNCLOSED
  "@startuml\n\n!include <archimate/Archimate>\n\nGrouping(g1, \"G\") {\n  Business_Actor(ba4, \"Мама\")\n\n@enduml")

(def ^:private LINT-SEQUENCE
  "@startuml\nAlice -> Bob: Authentication Request\nBob --> Alice: Authentication Response\n@enduml")

(deftest lint-plantuml-valid
  (testing "a valid ArchiMate document returns OK with no closing line"
    (let [[_ rc] (tools/handle-tool "lint_plantuml" {} {:content LINT-VALID})]
      (is (ok? rc))
      (is (= "OK: no problems" (txt rc))))))

(deftest lint-plantuml-unspecified-relation
  (testing "a disallowed relationship is reported as a warning plus the closing line"
    (let [[_ rc] (tools/handle-tool "lint_plantuml" {} {:content LINT-UNSPECIFIED})
          t (txt rc)]
      (is (ok? rc))
      (is (s/includes? t "0 errors, 1 warnings"))
      (is (s/includes? t "WARN line 12"))
      (is (s/includes? t "unspecified-relation-type"))
      (is (s/includes? t "Fix the errors and warnings above and lint again")))))

(deftest lint-plantuml-undefined-relation
  (testing "an unknown construct is reported as an error"
    (let [content (s/replace LINT-VALID "Rel_Assignment(ba4, bpc17)"
                             "Rel_Assignment(ba4, bpc17)\n??? bogus line")
          [_ rc] (tools/handle-tool "lint_plantuml" {} {:content content})
          t (txt rc)]
      (is (ok? rc))
      (is (s/includes? t "ERROR"))
      (is (s/includes? t "Fix the errors and warnings above and lint again")))))

(deftest lint-plantuml-unclosed-block
  (testing "an unclosed block is a lint, not an error response"
    (let [[_ rc] (tools/handle-tool "lint_plantuml" {} {:content LINT-UNCLOSED})
          t (txt rc)]
      (is (ok? rc))
      (is (s/includes? t "ERROR line 5 [in parse] unclosed-block"))
      (is (s/includes? t "Fix the errors and warnings above and lint again")))))

(deftest lint-plantuml-missing-and-blank
  (testing "missing and blank content are argument errors"
    (let [[_ rc1] (tools/handle-tool "lint_plantuml" {} {})
          [_ rc2] (tools/handle-tool "lint_plantuml" {} {:content "   \n  "})
          [_ rc3] (tools/handle-tool "lint_plantuml" {} {:content nil})]
      (is (not (ok? rc1)))
      (is (not (ok? rc2)))
      (is (not (ok? rc3)))
      (is (s/includes? (txt rc1) "content")))))

(deftest lint-plantuml-standalone-empty-registry
  (testing "lint_plantuml needs no loaded model and leaves the registry unchanged"
    (let [[r rc] (tools/handle-tool "lint_plantuml" {} {:content LINT-VALID})]
      (is (ok? rc))
      (is (= {} r))
      (is (= "OK: no problems" (txt rc))))))

(deftest lint-plantuml-not-archimate
  (testing "a non-ArchiMate diagram returns a report (no crash), not an isError"
    (let [[_ rc] (tools/handle-tool "lint_plantuml" {} {:content LINT-SEQUENCE})]
      (is (ok? rc))
      (is (s/includes? (txt rc) "errors")))))