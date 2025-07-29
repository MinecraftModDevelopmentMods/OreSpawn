# TODO
## CraftTweaker Integration Specific
### Goals
Provide for simplified interface so pack creators can add new forms of ore-body generation
without needing to have it added in a hard-coded manner to MMD OreSpawn.

### Items
1) Do Initialization Tasks, if any
2) Register Loader
3) Register block and blockstate brackets as needed
4) Add generics and interfaces to vanilla world-gen classes and types related

### Notes
The last item on the above list is the most complex, overall. What to do about it is a
major question, as I'm unsure of a way to actually manage the creation of a 
`public static final` for the CODEC, though I am going to attempt to make this happen, overall. 

That and the generic that wraps the Configuration containing the codec and provides the actual
ore-placement code are the most complex items. It might be best to just provide a basic setup
that wraps the vanilla class -- or perhaps one that wraps a custom class that provides for a
couple extra generically named values -- and then let the generation code provided by the 
user be a lambda or similar ?

