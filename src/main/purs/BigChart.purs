module BigChart
  ( reactClass
  ) where

import Effect (Effect)
import Prelude hiding (div)
import React (ReactClass, component, getProps)
import React.DOM (canvas)
import ReactOps (Ref, ref', createRef)
import Schema (NumPoint, StrPoint)

type State = {}
type Props =
  { cpuPoints :: Array NumPoint
  , memPoints :: Array NumPoint
  , actionPoints :: Array StrPoint
  }

reactClass :: ReactClass Props
reactClass = component "BigChart" \this -> do
  p <- getProps this
  let r = createRef
  pure
    { state: {}
    , render: pure $ canvas [ ref' r ] []
    , componentDidMount: createChart r p
    , componentDidUpdate: \p' _ _ -> updateChart p'
    , componentWillUnmount: destroyChart
    }

foreign import createChart
  :: Ref
  -> Props
  -> Effect Unit
foreign import updateChart
  :: Props
  -> Effect Unit
foreign import destroyChart
  :: Effect Unit
